package ai.agentican.framework.llm;

import ai.agentican.framework.tools.ToolDefinition;

import java.util.List;

public record LlmRequest(
        String systemPrompt,
        String userTask,
        String userMessage,
        List<ToolDefinition> tools,
        int iteration,
        String llmName,
        String provider,
        String model) {

    public LlmRequest {

        if (systemPrompt == null || systemPrompt.isBlank())
            throw new IllegalArgumentException("System prompt is required");

        var hasUserContent = (userMessage != null && !userMessage.isBlank())
                || (userTask != null && !userTask.isBlank());

        if (!hasUserContent)
            throw new IllegalArgumentException("User task or user message is required");

        if (userMessage == null) userMessage = "";

        if (tools == null)
            tools = List.of();
    }
}
