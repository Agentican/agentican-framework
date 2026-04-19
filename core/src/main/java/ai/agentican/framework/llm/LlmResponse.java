package ai.agentican.framework.llm;

import java.util.List;

public record LlmResponse(
        String text,
        List<ToolCall> toolCalls,
        StopReason stopReason,
        long inputTokens,
        long outputTokens,
        long cacheReadTokens,
        long cacheWriteTokens,
        long webSearchRequests) {

    public LlmResponse {

        if (stopReason == null)
            throw new IllegalArgumentException("Stop reason is required");

        if (toolCalls == null)
            toolCalls = List.of();
    }

    public TokenUsage tokenUsage() {

        return new TokenUsage(inputTokens, outputTokens, cacheReadTokens, cacheWriteTokens, webSearchRequests);
    }
}
