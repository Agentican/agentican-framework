package ai.agentican.quarkus.event;

public record PlanStartedEvent(String taskId, String taskDescription) {}
