package ai.agentican.quarkus.event;

public record ToolCallStartedEvent(String toolCallId, String turnId, String toolName,
                                   String taskId) {}
