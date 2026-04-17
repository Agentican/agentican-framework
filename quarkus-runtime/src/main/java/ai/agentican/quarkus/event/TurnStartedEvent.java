package ai.agentican.quarkus.event;

public record TurnStartedEvent(String turnId, String runId, String agentName, int turn,
                               String taskId) {}
