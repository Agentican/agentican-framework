package ai.agentican.quarkus.metrics;

import ai.agentican.framework.llm.LlmClient;
import ai.agentican.framework.llm.LlmRequest;
import ai.agentican.framework.llm.LlmResponse;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

public class MeteredLlmClient implements LlmClient {

    private final LlmClient delegate;
    private final String llmName;
    private final String model;
    private final MeterRegistry registry;

    public MeteredLlmClient(String llmName, String model, LlmClient delegate, MeterRegistry registry) {

        this.delegate = delegate;
        this.llmName = llmName;
        this.model = model;
        this.registry = registry;
    }

    @Override
    public LlmResponse send(LlmRequest request) {

        var sample = Timer.start(registry);

        try {

            var response = delegate.send(request);

            sample.stop(registry.timer("agentican.llm.duration", "llm", llmName, "model", model));

            registry.counter("agentican.llm.requests", "llm", llmName, "model", model,
                    "stop_reason", response.stopReason().name()).increment();

            registry.counter("agentican.llm.tokens.input", "llm", llmName, "model", model)
                    .increment(response.inputTokens());

            registry.counter("agentican.llm.tokens.output", "llm", llmName, "model", model)
                    .increment(response.outputTokens());

            registry.counter("agentican.llm.tokens.cache_read", "llm", llmName, "model", model)
                    .increment(response.cacheReadTokens());

            registry.counter("agentican.llm.tokens.cache_write", "llm", llmName, "model", model)
                    .increment(response.cacheWriteTokens());

            registry.counter("agentican.llm.web_searches", "llm", llmName, "model", model)
                    .increment(response.webSearchRequests());

            return response;

        }
        catch (Exception e) {

            sample.stop(registry.timer("agentican.llm.duration", "llm", llmName, "model", model));

            registry.counter("agentican.llm.errors", "llm", llmName, "model", model,
                    "error", e.getClass().getSimpleName()).increment();

            throw e;
        }
    }

    @Override
    public LlmResponse sendStreaming(LlmRequest request, java.util.function.Consumer<String> onToken) {

        var sample = Timer.start(registry);

        try {

            var response = delegate.sendStreaming(request, onToken);

            sample.stop(registry.timer("agentican.llm.duration", "llm", llmName, "model", model));

            registry.counter("agentican.llm.requests", "llm", llmName, "model", model,
                    "stop_reason", response.stopReason().name()).increment();

            registry.counter("agentican.llm.tokens.input", "llm", llmName, "model", model)
                    .increment(response.inputTokens());

            registry.counter("agentican.llm.tokens.output", "llm", llmName, "model", model)
                    .increment(response.outputTokens());

            registry.counter("agentican.llm.tokens.cache_read", "llm", llmName, "model", model)
                    .increment(response.cacheReadTokens());

            registry.counter("agentican.llm.tokens.cache_write", "llm", llmName, "model", model)
                    .increment(response.cacheWriteTokens());

            registry.counter("agentican.llm.web_searches", "llm", llmName, "model", model)
                    .increment(response.webSearchRequests());

            return response;

        }
        catch (Exception e) {

            sample.stop(registry.timer("agentican.llm.duration", "llm", llmName, "model", model));

            registry.counter("agentican.llm.errors", "llm", llmName, "model", model,
                    "error", e.getClass().getSimpleName()).increment();

            throw e;
        }
    }
}
