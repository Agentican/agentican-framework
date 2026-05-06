package ai.agentican.quarkus.event;

public record WfRunStartedEvent(String taskId, String taskDescription) {}
