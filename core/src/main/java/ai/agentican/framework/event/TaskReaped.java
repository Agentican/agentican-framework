package ai.agentican.framework.event;

import ai.agentican.framework.orchestration.execution.resume.ReapReason;

public record TaskReaped(String taskId, ReapReason reason) implements AgenticanEvent { }
