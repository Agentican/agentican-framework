package ai.agentican.quarkus.event;

import ai.agentican.framework.orchestration.execution.WorkflowRunStatus;

public record StepCompletedEvent(String stepId, String taskId, String stepName, WorkflowRunStatus status) {}
