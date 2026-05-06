package ai.agentican.quarkus.event;

public record WfRunCompletedEvent(String taskId, String taskName, String planId) {}
