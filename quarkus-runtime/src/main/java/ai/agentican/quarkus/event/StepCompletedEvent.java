package ai.agentican.quarkus.event;

import ai.agentican.framework.orchestration.execution.TaskStatus;

public record StepCompletedEvent(String stepId, String taskId, String stepName, TaskStatus status) {}
