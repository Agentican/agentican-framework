package ai.agentican.framework.event;

import ai.agentican.framework.llm.ToolCall;

public record ToolCallStarted(String taskId, String turnId, ToolCall toolCall) implements AgenticanEvent { }
