package ai.agentican.framework.llm;

import ai.agentican.framework.tools.ToolDefinition;

import java.util.List;

public record LlmRequest(
        String systemPrompt,
        String userMessage,
        List<ToolDefinition> tools,
        int iteration,
        String llmName,
        String provider,
        String model) {

    public LlmRequest {

        if (systemPrompt == null || systemPrompt.isBlank())
            throw new IllegalArgumentException("System prompt is required");

        if (userMessage == null || userMessage.isBlank())
            throw new IllegalArgumentException("User message is required");

        if (tools == null)
            tools = List.of();
    }

    public LlmRequest(String systemPrompt, String userMessage, List<ToolDefinition> tools, int iteration) {

        this(systemPrompt, userMessage, tools, iteration, null, null, null);
    }
}
