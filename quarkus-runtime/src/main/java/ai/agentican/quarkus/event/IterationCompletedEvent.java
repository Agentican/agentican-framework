package ai.agentican.quarkus.event;

import ai.agentican.framework.orchestration.execution.TaskStatus;

public record IterationCompletedEvent(
        String iterationId,
        String parentStepId,
        String parentTaskId,
        TaskStatus status) {}
