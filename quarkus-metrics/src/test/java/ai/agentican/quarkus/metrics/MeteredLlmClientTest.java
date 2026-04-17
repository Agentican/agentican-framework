package ai.agentican.quarkus.metrics;

import ai.agentican.framework.llm.LlmClient;
import ai.agentican.framework.llm.LlmRequest;
import ai.agentican.framework.llm.LlmResponse;
import ai.agentican.framework.llm.StopReason;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MeteredLlmClientTest {

    private SimpleMeterRegistry registry;

    @BeforeEach
    void setUp() {

        registry = new SimpleMeterRegistry();
    }

    @Test
    void recordsTokenCountersOnSuccess() {

        var response = LlmResponse.of("hello", List.of(), StopReason.END_TURN, 100, 50, 10, 20, 1);
        LlmClient delegate = request -> response;

        var metered = new MeteredLlmClient("default", "claude-sonnet-4-5", delegate, registry);
        metered.send(dummyRequest());

        assertEquals(1.0, counter("agentican.llm.requests", "stop_reason", "END_TURN"));
        assertEquals(100.0, counter("agentican.llm.tokens.input"));
        assertEquals(50.0, counter("agentican.llm.tokens.output"));
        assertEquals(10.0, counter("agentican.llm.tokens.cache_read"));
        assertEquals(20.0, counter("agentican.llm.tokens.cache_write"));
        assertEquals(1.0, counter("agentican.llm.web_searches"));
    }

    @Test
    void recordsDurationTimerOnSuccess() {

        var response = LlmResponse.of("ok", List.of(), StopReason.END_TURN, 10, 10, 0, 0, 0);
        LlmClient delegate = request -> response;

        var metered = new MeteredLlmClient("default", "claude-sonnet-4-5", delegate, registry);
        metered.send(dummyRequest());

        var timer = registry.timer("agentican.llm.duration", "llm", "default", "model", "claude-sonnet-4-5");
        assertEquals(1, timer.count());
    }

    @Test
    void recordsErrorCounterOnFailure() {

        LlmClient delegate = request -> { throw new RuntimeException("API error"); };

        var metered = new MeteredLlmClient("fast", "claude-haiku-4-5", delegate, registry);

        assertThrows(RuntimeException.class, () -> metered.send(dummyRequest()));

        assertEquals(1.0, registry.counter("agentican.llm.errors",
                "llm", "fast", "model", "claude-haiku-4-5", "error", "RuntimeException").count());

        var timer = registry.timer("agentican.llm.duration", "llm", "fast", "model", "claude-haiku-4-5");
        assertEquals(1, timer.count());
    }

    @Test
    void tagsWithLlmNameAndModel() {

        var response = LlmResponse.of("hi", List.of(), StopReason.END_TURN, 5, 5, 0, 0, 0);
        LlmClient delegate = request -> response;

        var metered = new MeteredLlmClient("custom", "gpt-4o", delegate, registry);
        metered.send(dummyRequest());

        assertNotNull(registry.find("agentican.llm.requests").tag("llm", "custom").tag("model", "gpt-4o").counter());
    }

    @Test
    void accumulatesAcrossMultipleCalls() {

        var response = LlmResponse.of("hi", List.of(), StopReason.END_TURN, 100, 50, 0, 0, 0);
        LlmClient delegate = request -> response;

        var metered = new MeteredLlmClient("default", "claude-sonnet-4-5", delegate, registry);
        metered.send(dummyRequest());
        metered.send(dummyRequest());
        metered.send(dummyRequest());

        assertEquals(3.0, counter("agentican.llm.requests", "stop_reason", "END_TURN"));
        assertEquals(300.0, counter("agentican.llm.tokens.input"));
        assertEquals(150.0, counter("agentican.llm.tokens.output"));
    }

    private double counter(String name, String... extraTags) {

        var search = registry.find(name).tag("llm", "default").tag("model", "claude-sonnet-4-5");
        for (int i = 0; i < extraTags.length; i += 2) search = search.tag(extraTags[i], extraTags[i + 1]);
        var counter = search.counter();
        return counter != null ? counter.count() : 0;
    }

    private static LlmRequest dummyRequest() {

        return new LlmRequest("system prompt", null, "user message", List.of(), 0, null, null, null);
    }
}
