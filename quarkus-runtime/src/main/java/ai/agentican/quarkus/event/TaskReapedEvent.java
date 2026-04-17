package ai.agentican.quarkus.event;

import ai.agentican.framework.orchestration.execution.resume.ReapReason;

public record TaskReapedEvent(String taskId, ReapReason reason) {}
