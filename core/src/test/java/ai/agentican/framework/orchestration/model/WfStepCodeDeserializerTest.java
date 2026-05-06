package ai.agentican.framework.orchestration.model;

import ai.agentican.framework.orchestration.code.CodeStep;
import ai.agentican.framework.orchestration.code.CodeStepRegistry;
import ai.agentican.framework.orchestration.code.CodeStepSpec;
import ai.agentican.framework.util.Json;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class WfStepCodeDeserializerTest {

    record HttpInput(String url, String method) { }

    @Test
    void roundTripWithTypedInputUsingCodec() throws Exception {

        var registry = new CodeStepRegistry();
        registry.register(new CodeStepSpec<>("http", null, HttpInput.class, String.class),
                (CodeStep<HttpInput, String>) (input, ctx) -> "ok");

        var codec = new WorkflowDefinitionCodec(registry);

        var step = new WorkflowStepCode<>("fetch", "http",
                new HttpInput("https://example.com", "GET"), List.of("upstream"));

        var plan = WorkflowDefinition.builder("test-definition").description("desc").step(step).build();

        var json = Json.writeValueAsString(plan);
        var roundTripped = codec.fromJson(json, WorkflowDefinition.class);

        var deserStep = (WorkflowStepCode<?>) roundTripped.steps().getFirst();
        assertEquals("fetch", deserStep.name());
        assertEquals("http", deserStep.codeSlug());
        assertEquals(List.of("upstream"), deserStep.dependencies());

        assertInstanceOf(HttpInput.class, deserStep.input());
        var typed = (HttpInput) deserStep.input();
        assertEquals("https://example.com", typed.url());
        assertEquals("GET", typed.method());
    }

    @Test
    void deserWithoutCodecLeavesInputAsJsonNode() throws Exception {

        var step = new WorkflowStepCode<>("fetch", "http",
                new HttpInput("https://example.com", "GET"), List.of());

        var plan = WorkflowDefinition.builder("test-definition").description("desc").step(step).build();

        var json = Json.writeValueAsString(plan);
        var roundTripped = Json.readValue(json, WorkflowDefinition.class);

        var deserStep = (WorkflowStepCode<?>) roundTripped.steps().getFirst();

        assertInstanceOf(JsonNode.class, deserStep.input());
        var node = (JsonNode) deserStep.input();
        assertEquals("https://example.com", node.get("url").asText());
        assertEquals("GET", node.get("method").asText());
    }

    @Test
    void unknownSlugWithCodecThrows() {

        var registry = new CodeStepRegistry();
        var codec = new WorkflowDefinitionCodec(registry);

        var json = """
                {"id":"definition-1","name":"p","description":"d","params":[],"steps":[
                  {"type":"code","name":"x","codeSlug":"missing","input":null,"dependencies":[]}
                ]}
                """;

        assertThrows(Exception.class, () -> codec.fromJson(json, WorkflowDefinition.class));
    }

    @Test
    void nullInputRoundTrip() throws Exception {

        var registry = new CodeStepRegistry();
        registry.register(new CodeStepSpec<>("noop", null, Void.class, Void.class),
                (CodeStep<Void, Void>) (input, ctx) -> null);

        var codec = new WorkflowDefinitionCodec(registry);

        var step = new WorkflowStepCode<>("n", "noop", null, List.of());
        var plan = WorkflowDefinition.builder("p").description("d").step(step).build();

        var json = Json.writeValueAsString(plan);
        var back = codec.fromJson(json, WorkflowDefinition.class);

        var deserStep = (WorkflowStepCode<?>) back.steps().getFirst();
        assertNull(deserStep.input());
    }
}
