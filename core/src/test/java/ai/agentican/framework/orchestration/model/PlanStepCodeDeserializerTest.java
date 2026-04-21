package ai.agentican.framework.orchestration.model;

import ai.agentican.framework.orchestration.code.CodeStep;
import ai.agentican.framework.orchestration.code.CodeStepRegistry;
import ai.agentican.framework.orchestration.code.CodeStepSpec;
import ai.agentican.framework.util.Json;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PlanStepCodeDeserializerTest {

    record HttpInput(String url, String method) { }

    @Test
    void roundTripWithTypedInputUsingCodec() throws Exception {

        var registry = new CodeStepRegistry();
        registry.register(new CodeStepSpec<>("http", null, HttpInput.class, String.class),
                (CodeStep<HttpInput, String>) (input, ctx) -> "ok");

        var codec = new PlanCodec(registry);

        var step = new PlanStepCode<>("fetch", "http",
                new HttpInput("https://example.com", "GET"), List.of("upstream"));

        var plan = Plan.builder("test-plan").description("desc").step(step).build();

        var json = Json.writeValueAsString(plan);
        var roundTripped = codec.fromJson(json, Plan.class);

        var deserStep = (PlanStepCode<?>) roundTripped.steps().getFirst();
        assertEquals("fetch", deserStep.name());
        assertEquals("http", deserStep.codeSlug());
        assertEquals(List.of("upstream"), deserStep.dependencies());

        // Input typed as HttpInput because the codec injected the registry
        assertInstanceOf(HttpInput.class, deserStep.input());
        var typed = (HttpInput) deserStep.input();
        assertEquals("https://example.com", typed.url());
        assertEquals("GET", typed.method());
    }

    @Test
    void deserWithoutCodecLeavesInputAsJsonNode() throws Exception {

        var step = new PlanStepCode<>("fetch", "http",
                new HttpInput("https://example.com", "GET"), List.of());

        var plan = Plan.builder("test-plan").description("desc").step(step).build();

        var json = Json.writeValueAsString(plan);
        var roundTripped = Json.readValue(json, Plan.class);

        var deserStep = (PlanStepCode<?>) roundTripped.steps().getFirst();
        // Without the codec injecting the registry, the deserializer falls
        // back to a JsonNode so plan structure can still be inspected.
        assertInstanceOf(JsonNode.class, deserStep.input());
        var node = (JsonNode) deserStep.input();
        assertEquals("https://example.com", node.get("url").asText());
        assertEquals("GET", node.get("method").asText());
    }

    @Test
    void unknownSlugWithCodecThrows() {

        var registry = new CodeStepRegistry();
        var codec = new PlanCodec(registry);

        var json = """
                {"id":"plan-1","name":"p","description":"d","params":[],"steps":[
                  {"type":"code","name":"x","codeSlug":"missing","input":null,"dependencies":[]}
                ]}
                """;

        assertThrows(Exception.class, () -> codec.fromJson(json, Plan.class));
    }

    @Test
    void nullInputRoundTrip() throws Exception {

        var registry = new CodeStepRegistry();
        registry.register(new CodeStepSpec<>("noop", null, Void.class, Void.class),
                (CodeStep<Void, Void>) (input, ctx) -> null);

        var codec = new PlanCodec(registry);

        var step = new PlanStepCode<>("n", "noop", null, List.of());
        var plan = Plan.builder("p").description("d").step(step).build();

        var json = Json.writeValueAsString(plan);
        var back = codec.fromJson(json, Plan.class);

        var deserStep = (PlanStepCode<?>) back.steps().getFirst();
        assertNull(deserStep.input());
    }
}
