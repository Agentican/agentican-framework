package ai.agentican.quarkus.otel;

import ai.agentican.framework.llm.LlmClient;
import ai.agentican.framework.llm.LlmRequest;
import ai.agentican.framework.llm.LlmResponse;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;

import java.util.function.Consumer;

public class TracedLlmClient implements LlmClient {

    private static final String LLM_SEND_SPAN = "agentican.llm.send";

    private static final AttributeKey<String> LLM_NAME = AttributeKey.stringKey("agentican.llm.name");
    private static final AttributeKey<String> GEN_AI_SYSTEM = AttributeKey.stringKey("gen_ai.system");
    private static final AttributeKey<String> GEN_AI_MODEL = AttributeKey.stringKey("gen_ai.request.model");
    private static final AttributeKey<Long> GEN_AI_INPUT_TOKENS = AttributeKey.longKey("gen_ai.usage.input_tokens");
    private static final AttributeKey<Long> GEN_AI_OUTPUT_TOKENS = AttributeKey.longKey("gen_ai.usage.output_tokens");
    private static final AttributeKey<Long> GEN_AI_CACHE_READ = AttributeKey.longKey("gen_ai.usage.cache_read_tokens");
    private static final AttributeKey<Long> GEN_AI_CACHE_WRITE = AttributeKey.longKey("gen_ai.usage.cache_write_tokens");
    private static final AttributeKey<String> GEN_AI_FINISH = AttributeKey.stringKey("gen_ai.response.finish_reasons");

    private final LlmClient delegate;
    private final String llmName;
    private final String model;
    private final Tracer tracer;

    public TracedLlmClient(String llmName, String model, LlmClient delegate, Tracer tracer) {

        this.delegate = delegate;
        this.llmName = llmName;
        this.model = model;
        this.tracer = tracer;
    }

    @Override
    public LlmResponse send(LlmRequest request) {

        return traced(() -> delegate.send(request), request);
    }

    @Override
    public LlmResponse sendStreaming(LlmRequest request, Consumer<String> onToken) {

        return traced(() -> delegate.sendStreaming(request, onToken), request);
    }

    private LlmResponse traced(java.util.function.Supplier<LlmResponse> call, LlmRequest request) {

        var builder = tracer.spanBuilder(LLM_SEND_SPAN).setSpanKind(SpanKind.CLIENT)
                .setAttribute(LLM_NAME, llmName)
                .setAttribute(GEN_AI_MODEL, model);

        if (request != null && request.provider() != null)
            builder.setAttribute(GEN_AI_SYSTEM, request.provider());

        var span = builder.startSpan();

        try (var scope = span.makeCurrent()) {

            var response = call.get();

            if (response != null) {

                span.setAttribute(GEN_AI_INPUT_TOKENS, response.inputTokens());
                span.setAttribute(GEN_AI_OUTPUT_TOKENS, response.outputTokens());
                span.setAttribute(GEN_AI_CACHE_READ, response.cacheReadTokens());
                span.setAttribute(GEN_AI_CACHE_WRITE, response.cacheWriteTokens());

                if (response.stopReason() != null)
                    span.setAttribute(GEN_AI_FINISH, response.stopReason().name());
            }

            span.setStatus(StatusCode.OK);

            return response;
        }
        catch (RuntimeException e) {

            span.setStatus(StatusCode.ERROR, e.getClass().getSimpleName());
            span.recordException(e);

            throw e;
        }
        finally {

            span.end();
        }
    }
}
