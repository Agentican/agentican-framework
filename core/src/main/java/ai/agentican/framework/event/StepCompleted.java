package ai.agentican.framework.event;

import ai.agentican.framework.orchestration.execution.WorkflowRunStatus;

public record StepCompleted(String taskId, String stepId, String stepName,
                            WorkflowRunStatus status, String output) implements AgenticanEvent { }
