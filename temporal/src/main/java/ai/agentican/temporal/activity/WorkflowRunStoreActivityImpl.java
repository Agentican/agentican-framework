package ai.agentican.temporal.activity;

import ai.agentican.framework.hitl.HitlCheckpoint;
import ai.agentican.framework.hitl.HitlResponse;
import ai.agentican.framework.llm.LlmRequest;
import ai.agentican.framework.llm.LlmResponse;
import ai.agentican.framework.llm.TokenUsage;
import ai.agentican.framework.llm.ToolCall;
import ai.agentican.framework.orchestration.execution.WorkflowRunStatus;
import ai.agentican.framework.state.WorkflowRunLog;
import ai.agentican.framework.store.WorkflowRunStore;
import ai.agentican.framework.tools.ToolResult;
import ai.agentican.temporal.dto.TaskStartedRequest;

import java.util.List;

public class WorkflowRunStoreActivityImpl implements WorkflowRunStoreActivity {

    private final WorkflowRunStore delegate;

    public WorkflowRunStoreActivityImpl(WorkflowRunStore delegate) {

        if (delegate == null) throw new IllegalArgumentException("delegate WorkflowRunStore is required");

        this.delegate = delegate;
    }

    @Override
    public void taskStarted(TaskStartedRequest r) {

        if (r.parentTaskId() == null)
            delegate.taskStarted(r.taskId(), r.taskName(), r.plan(), r.params());
        else
            delegate.taskStarted(r.taskId(), r.taskName(), r.plan(), r.params(), r.parentTaskId(),
                    r.parentStepId(), r.iterationIndex());
    }

    @Override public void taskCompleted(String taskId, WorkflowRunStatus status) {

        delegate.taskCompleted(taskId, status);
    }

    @Override public void stepStarted(String taskId, String stepId, String stepName) {

        delegate.stepStarted(taskId, stepId, stepName);
    }

    @Override public void stepCompleted(String taskId, String stepId, WorkflowRunStatus status, String output) {

        delegate.stepCompleted(taskId, stepId, status, output);
    }

    @Override public void stepTokenUsageAggregated(String taskId, String stepId, TokenUsage usage) {

        delegate.stepTokenUsageAggregated(taskId, stepId, usage);
    }

    @Override public void runStarted(String taskId, String stepId, String runId, String agentName) {

        delegate.runStarted(taskId, stepId, runId, agentName);
    }

    @Override public void runCompleted(String taskId, String runId) {

        delegate.runCompleted(taskId, runId);
    }

    @Override public void turnStarted(String taskId, String runId, String turnId) {

        delegate.turnStarted(taskId, runId, turnId);
    }

    @Override public void turnCompleted(String taskId, String turnId) {

        delegate.turnCompleted(taskId, turnId);
    }

    @Override public void turnAbandoned(String taskId, String turnId) {

        delegate.turnAbandoned(taskId, turnId);
    }

    @Override public void messageSent(String taskId, String turnId, LlmRequest request) {

        delegate.messageSent(taskId, turnId, request);
    }

    @Override public void responseReceived(String taskId, String turnId, LlmResponse response) {

        delegate.responseReceived(taskId, turnId, response);
    }

    @Override public void toolCallStarted(String taskId, String turnId, ToolCall toolCall) {

        delegate.toolCallStarted(taskId, turnId, toolCall);
    }

    @Override public void toolCallCompleted(String taskId, String turnId, ToolResult toolResult) {

        delegate.toolCallCompleted(taskId, turnId, toolResult);
    }

    @Override public void hitlNotified(String taskId, String stepId, HitlCheckpoint checkpoint) {

        delegate.hitlNotified(taskId, stepId, checkpoint);
    }

    @Override public void hitlResponded(String taskId, String stepId, HitlResponse response) {

        delegate.hitlResponded(taskId, stepId, response);
    }

    @Override public void branchPathChosen(String taskId, String stepId, String pathName) {

        delegate.branchPathChosen(taskId, stepId, pathName);
    }

    @Override public WorkflowRunLog load(String taskId) { return delegate.load(taskId); }
    @Override public List<WorkflowRunLog> list() { return delegate.list(); }
    @Override public List<WorkflowRunLog> listInProgress() { return delegate.listInProgress(); }
}
