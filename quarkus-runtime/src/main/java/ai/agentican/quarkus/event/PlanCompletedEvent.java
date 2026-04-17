package ai.agentican.quarkus.event;

public record PlanCompletedEvent(String taskId, String taskName, String planId) {}
