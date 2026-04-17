package ai.agentican.quarkus.event;

public record MessageSentEvent(String messageId, String turnId, String agentName, int turn,
                               String taskId) {}
