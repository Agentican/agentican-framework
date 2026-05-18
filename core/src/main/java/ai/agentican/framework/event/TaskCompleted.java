package ai.agentican.framework.event;

import ai.agentican.framework.orchestration.execution.WorkflowRunStatus;

public record TaskCompleted(String taskId, WorkflowRunStatus status) implements AgenticanEvent { }
