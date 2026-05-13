package ai.agentican.temporal.dto;

import ai.agentican.framework.llm.TokenUsage;

public record TokenUsageDto(
        long input,
        long output,
        long cacheRead,
        long cacheWrite,
        long webSearches) {

    public static TokenUsageDto from(TokenUsage u) {

        if (u == null) return null;

        return new TokenUsageDto(u.input(), u.output(), u.cacheRead(), u.cacheWrite(), u.webSearches());
    }
}
