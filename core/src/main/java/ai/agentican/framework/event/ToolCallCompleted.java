package ai.agentican.framework.event;

import ai.agentican.framework.tools.ToolResult;

public record ToolCallCompleted(String taskId, String turnId, ToolResult toolResult) implements AgenticanEvent { }
