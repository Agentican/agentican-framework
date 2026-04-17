package ai.agentican.quarkus.event;

public record TurnCompletedEvent(String turnId, String runId, String agentName, int turn,
                                 String taskId) {}
