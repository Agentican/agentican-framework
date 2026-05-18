package ai.agentican.framework.event;

import ai.agentican.framework.store.WorkflowRunStore;

public final class WorkflowRunStorePersister implements AgenticanEventListener {

    private final WorkflowRunStore store;

    public WorkflowRunStorePersister(WorkflowRunStore store) {

        if (store == null) throw new IllegalArgumentException("store is required");

        this.store = store;
    }

    @Override
    public void on(AgenticanEvent event) {

        switch (event) {

            case TaskStarted e -> {

                if (e.parentTaskId() == null)
                    store.taskStarted(e.taskId(), e.taskName(), e.plan(), e.params(),
                            e.runtime(), e.temporalWorkflowId());
                else
                    store.taskStarted(e.taskId(), e.taskName(), e.plan(), e.params(),
                            e.parentTaskId(), e.parentStepId(), e.iterationIndex(),
                            e.runtime(), e.temporalWorkflowId());
            }

            case TaskCompleted e -> store.taskCompleted(e.taskId(), e.status());

            case StepStarted e   -> store.stepStarted(e.taskId(), e.stepId(), e.stepName());
            case StepCompleted e -> store.stepCompleted(e.taskId(), e.stepId(), e.status(), e.output());

            case StepTokenUsageAggregated e ->
                    store.stepTokenUsageAggregated(e.taskId(), e.stepId(), e.tokenUsage());

            case BranchPathChosen e ->
                    store.branchPathChosen(e.taskId(), e.stepId(), e.pathName());

            case RunStarted e   -> store.runStarted(e.taskId(), e.stepId(), e.runId(), e.agentName());
            case RunCompleted e -> store.runCompleted(e.taskId(), e.runId());

            case TurnStarted e   -> store.turnStarted(e.taskId(), e.runId(), e.turnId());
            case TurnCompleted e -> store.turnCompleted(e.taskId(), e.turnId());
            case TurnAbandoned e -> store.turnAbandoned(e.taskId(), e.turnId());

            case MessageSent e      -> store.messageSent(e.taskId(), e.turnId(), e.request());
            case ResponseReceived e -> store.responseReceived(e.taskId(), e.turnId(), e.response());

            case ToolCallStarted e   -> store.toolCallStarted(e.taskId(), e.turnId(), e.toolCall());
            case ToolCallCompleted e -> store.toolCallCompleted(e.taskId(), e.turnId(), e.toolResult());

            case HitlNotified e  -> store.hitlNotified(e.taskId(), e.stepId(), e.checkpoint());
            case HitlResponded e -> store.hitlResponded(e.taskId(), e.stepId(), e.response());

            // Observability-only events with no store representation:
            case PlanStarted _    -> { }
            case PlanCompleted _  -> { }
            case TaskReaped _     -> { }
            case TaskResumed _    -> { }
            case StepResumed _    -> { }
            case RunResumed _     -> { }
            case TurnResumed _    -> { }
            case TokenStreamed _  -> { }
        }
    }
}
