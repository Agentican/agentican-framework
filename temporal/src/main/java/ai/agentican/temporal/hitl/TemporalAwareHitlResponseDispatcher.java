package ai.agentican.temporal.hitl;

import ai.agentican.framework.event.AgenticanEvent;
import ai.agentican.framework.event.AgenticanEventListener;
import ai.agentican.framework.event.HitlNotified;
import ai.agentican.framework.event.HitlResponded;
import ai.agentican.framework.event.TaskCompleted;
import ai.agentican.framework.hitl.HitlResponse;
import ai.agentican.framework.hitl.HitlResponseDispatcher;
import ai.agentican.framework.state.RuntimeOwner;
import ai.agentican.framework.store.WorkflowRunStore;
import ai.agentican.temporal.workflow.RunnerBasedAgentWorkflow;

import io.temporal.client.WorkflowClient;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public final class TemporalAwareHitlResponseDispatcher implements HitlResponseDispatcher, AgenticanEventListener {

    private final HitlResponseDispatcher fallback;
    private final WorkflowRunStore store;
    private final WorkflowClient workflowClient;

    private final ConcurrentHashMap<String, TemporalCheckpoint> temporalCheckpoints = new ConcurrentHashMap<>();

    private record TemporalCheckpoint(String taskId, String workflowId) { }

    public TemporalAwareHitlResponseDispatcher(HitlResponseDispatcher fallback,
                                               WorkflowRunStore store,
                                               WorkflowClient workflowClient) {

        this.fallback = Objects.requireNonNull(fallback, "fallback");
        this.store = Objects.requireNonNull(store, "store");
        this.workflowClient = Objects.requireNonNull(workflowClient, "workflowClient");
    }

    @Override
    public void on(AgenticanEvent event) {

        switch (event) {

            case HitlNotified e -> indexIfTemporal(e);
            case HitlResponded e -> {
                if (e.hitlId() != null) temporalCheckpoints.remove(e.hitlId());
            }
            case TaskCompleted e -> evictForTask(e.taskId());
            default -> { /* nothing */ }
        }
    }

    private void indexIfTemporal(HitlNotified event) {

        var task = store.load(event.taskId());

        if (task == null) return;
        if (task.runtime() != RuntimeOwner.TEMPORAL) return;
        if (task.temporalWorkflowId() == null) return;

        temporalCheckpoints.put(event.checkpoint().id(),
                new TemporalCheckpoint(event.taskId(), task.temporalWorkflowId()));
    }

    private void evictForTask(String taskId) {

        temporalCheckpoints.entrySet().removeIf(e -> taskId.equals(e.getValue().taskId()));
    }

    @Override
    public void respond(String checkpointId, HitlResponse response) {

        var info = temporalCheckpoints.remove(checkpointId);

        if (info == null) {
            fallback.respond(checkpointId, response);
            return;
        }

        signalWorkflow(info.workflowId(), checkpointId, response);
    }

    @Override
    public void cancel(String checkpointId) {

        var info = temporalCheckpoints.remove(checkpointId);

        if (info == null) {
            fallback.cancel(checkpointId);
            return;
        }

        // Temporal HITL semantics: synthesize a "rejected" response so the workflow
        // proceeds deterministically. The Temporal SDK doesn't have a native "drop
        // this signal" operation; the agent loop interprets approved=false uniformly.
        signalWorkflow(info.workflowId(), checkpointId,
                new HitlResponse(false, "Cancelled by operator"));
    }

    private void signalWorkflow(String workflowId, String checkpointId, HitlResponse response) {

        var stub = workflowClient.newWorkflowStub(RunnerBasedAgentWorkflow.class, workflowId);
        stub.provideHitlResponse(checkpointId, response);
    }
}
