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
        String model,
        StructuredOutput structuredOutput,
        List<Message> messages) {

    public LlmRequest {

        if (systemPrompt == null || systemPrompt.isBlank())
            throw new IllegalArgumentException("System prompt is required");

        var hasMessages = messages != null && !messages.isEmpty();

        var hasUserContent = (userMessage != null && !userMessage.isBlank())
                || (userTask != null && !userTask.isBlank());

        if (!hasMessages && !hasUserContent)
            throw new IllegalArgumentException("Either messages or userTask/userMessage is required");

        if (userMessage == null) userMessage = "";

        if (tools == null)
            tools = List.of();

        if (messages == null)
            messages = List.of();
        else
            messages = List.copyOf(messages);
    }
}
