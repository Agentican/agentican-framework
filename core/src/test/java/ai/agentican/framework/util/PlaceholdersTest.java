package ai.agentican.framework.util;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PlaceholdersTest {

    @Test
    void resolveParams() {

        var result = Placeholders.resolveParams(
                "Hello {{param.name}}, count={{param.count}}",
                Map.of("name", "World", "count", "5"));

        assertEquals("Hello World, count=5", result);
    }

    @Test
    void resolveStepOutputs() {

        var result = Placeholders.resolveStepOutputs(
                "Data: {{step.research.output}}",
                Map.of("research", "some data"));

        assertTrue(result.contains("<upstream-output step=\"research\">"), "Expected XML wrapper tag");
        assertTrue(result.contains("some data"), "Expected output content");
    }

    @Test
    void resolveItem() {

        var result = Placeholders.resolveItem(
                "Process {{item.name}} with {{item.id}}",
                "{\"name\": \"Test\", \"id\": \"123\"}");

        assertEquals("Process Test with 123", result);
    }

    @Test
    void resolveItemFullObject() {

        var result = Placeholders.resolveItem(
                "Full item: {{item}}",
                "{\"name\":\"Test\"}");

        assertEquals("Full item: {\"name\":\"Test\"}", result);
    }

    @Test
    void missingParamUnchanged() {

        var result = Placeholders.resolveParams(
                "Hello {{param.missing}}",
                Map.of());

        assertEquals("Hello {{param.missing}}", result);
    }

    @Test
    void resolveMultiplePlaceholders() {

        var result = Placeholders.resolveParams(
                "Hello {{param.first}} {{param.last}}, age {{param.age}}",
                Map.of("first", "John", "last", "Doe", "age", "30"));

        assertEquals("Hello John Doe, age 30", result);
    }

    @Test
    void missingStepOutputUnchanged() {

        var result = Placeholders.resolveStepOutputs(
                "Data: {{step.nonexistent.output}}",
                Map.of());

        assertEquals("Data: {{step.nonexistent.output}}", result);
    }

    @Test
    void resolveStepOutputFieldExtractsFromJson() {

        var result = Placeholders.resolveStepOutputs(
                "body={{step.fetch.output.body}} status={{step.fetch.output.status}}",
                Map.of("fetch", "{\"body\":\"hello\",\"status\":200}"));

        assertEquals("body=hello status=200", result);
    }

    @Test
    void resolveStepOutputFieldNestedPath() {

        var result = Placeholders.resolveStepOutputs(
                "name={{step.user.output.profile.name}}",
                Map.of("user", "{\"profile\":{\"name\":\"Alice\"}}"));

        assertEquals("name=Alice", result);
    }

    @Test
    void resolveStepOutputFieldMissingFieldYieldsEmpty() {

        var result = Placeholders.resolveStepOutputs(
                "missing=[{{step.fetch.output.nope}}]",
                Map.of("fetch", "{\"body\":\"hello\"}"));

        assertEquals("missing=[]", result);
    }

    @Test
    void resolveStepOutputFieldOnNonJsonYieldsEmpty() {

        var result = Placeholders.resolveStepOutputs(
                "field=[{{step.agent.output.body}}]",
                Map.of("agent", "this is plain agent text, not JSON"));

        assertEquals("field=[]", result);
    }

    @Test
    void resolveStepOutputsRawDoesNotWrap() {

        var result = Placeholders.resolveStepOutputsRaw(
                "Data: {{step.research.output}}",
                Map.of("research", "some data"));

        assertEquals("Data: some data", result);
        assertFalse(result.contains("<upstream-output"));
    }

    @Test
    void resolveStepOutputsRawSupportsFieldAccess() {

        var result = Placeholders.resolveStepOutputsRaw(
                "url={{step.fetch.output.url}}",
                Map.of("fetch", "{\"url\":\"https://x\"}"));

        assertEquals("url=https://x", result);
    }
}
