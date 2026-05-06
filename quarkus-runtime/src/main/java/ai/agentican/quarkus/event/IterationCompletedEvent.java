package ai.agentican.quarkus.event;

import ai.agentican.framework.orchestration.execution.WorkflowRunStatus;

public record IterationCompletedEvent(
        String iterationId,
        String parentStepId,
        String parentTaskId,
        WorkflowRunStatus status) {}
