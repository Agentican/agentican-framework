package ai.agentican.framework.llm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.function.Function;

public class RetryingLlmClient implements LlmClient {

    private static final Logger LOG = LoggerFactory.getLogger(RetryingLlmClient.class);
    private static final Duration MAX_RETRY_DELAY = Duration.ofSeconds(30);
    private static final int DEFAULT_JITTER_MS = 500;

    private final LlmClient delegate;
    private final int maxRetries;
    private final Duration baseDelay;

    public RetryingLlmClient(LlmClient delegate, int maxRetries, Duration baseDelay) {

        this.delegate = delegate;
        this.maxRetries = maxRetries > 0 ? maxRetries : 3;
        this.baseDelay = baseDelay != null ? baseDelay : Duration.ofSeconds(1);
    }

    @Override
    public LlmResponse send(LlmRequest request) {

        return retry(request, delegate::send, null);
    }

    @Override
    public LlmResponse sendStreaming(LlmRequest request, Consumer<String> onToken) {

        return retry(request, r -> delegate.sendStreaming(r, onToken), null);
    }

    private LlmResponse retry(LlmRequest request, Function<LlmRequest, LlmResponse> sender, Consumer<String> onToken) {

        Exception lastException = null;

        for (int attempt = 0; attempt <= maxRetries; attempt++) {

            try {

                return sender.apply(request);

            }
            catch (Exception e) {

                lastException = e;

                if (attempt >= maxRetries || !isRetryable(e)) throw wrapException(e);

                var delay = computeBackoffDelay(attempt);

                LOG.warn("LLM call failed (attempt {}/{}), retrying in {}ms: {}",
                        attempt + 1, maxRetries + 1, delay.toMillis(), e.getMessage());

                try {
                    Thread.sleep(delay.toMillis());
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw wrapException(e);
                }
            }
        }

        throw wrapException(lastException);
    }

    private Duration computeBackoffDelay(int attempt) {

        var exponentialMs = baseDelay.toMillis() * (1L << attempt);
        var jitterMs = ThreadLocalRandom.current().nextLong(DEFAULT_JITTER_MS);
        var totalMs = Math.min(exponentialMs + jitterMs, MAX_RETRY_DELAY.toMillis());

        return Duration.ofMillis(totalMs);
    }

    private static boolean isRetryable(Exception e) {

        if (e instanceof IOException) return true;
        if (e instanceof TimeoutException) return true;
        if (e.getCause() instanceof IOException) return true;
        if (e.getCause() instanceof TimeoutException) return true;

        var msg = e.getMessage();
        if (msg == null) return false;

        var lower = msg.toLowerCase();

        return lower.contains("429") || lower.contains("529")
                || lower.contains("500") || lower.contains("503")
                || lower.contains("rate_limit") || lower.contains("rate limit")
                || lower.contains("overloaded") || lower.contains("too many requests");
    }

    private static RuntimeException wrapException(Exception e) {

        if (e instanceof RuntimeException re) return re;
        return new RuntimeException(e);
    }
}
