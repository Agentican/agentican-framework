package ai.agentican.framework.llm;

import java.util.stream.Stream;

public record TokenUsage(long input, long output, long cacheRead, long cacheWrite, long webSearches) {

    public static final TokenUsage ZERO = new TokenUsage(0, 0, 0, 0, 0);

    public TokenUsage plus(TokenUsage other) {

        return new TokenUsage(
                input + other.input,
                output + other.output,
                cacheRead + other.cacheRead,
                cacheWrite + other.cacheWrite,
                webSearches + other.webSearches);
    }

    public static TokenUsage sum(Stream<TokenUsage> usages) {

        return usages.reduce(ZERO, TokenUsage::plus);
    }

    public long totalTokens() {

        return input + output;
    }
}
