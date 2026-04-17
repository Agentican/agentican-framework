package ai.agentican.quarkus.event;

public record ToolCallCompletedEvent(String toolCallId, String turnId, String toolName,
                                     boolean error, String taskId) {}
