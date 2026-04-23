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

    @Test
    void resolveParamFieldExtractsFromJson() {

        var result = Placeholders.resolveParams(
                "email={{param.user.email}} name={{param.user.name}}",
                Map.of("user", "{\"email\":\"a@b.com\",\"name\":\"Alice\"}"));

        assertEquals("email=a@b.com name=Alice", result);
    }

    @Test
    void resolveParamFieldNestedPath() {

        var result = Placeholders.resolveParams(
                "city={{param.user.address.city}}",
                Map.of("user", "{\"address\":{\"city\":\"Austin\"}}"));

        assertEquals("city=Austin", result);
    }

    @Test
    void resolveParamFieldMixedWithSingleSegment() {

        var result = Placeholders.resolveParams(
                "Hello {{param.name}}, email={{param.user.email}}",
                Map.of("name", "Bob", "user", "{\"email\":\"b@c.com\"}"));

        assertEquals("Hello Bob, email=b@c.com", result);
    }

    @Test
    void resolveParamFieldMissingFieldYieldsEmpty() {

        var result = Placeholders.resolveParams(
                "missing=[{{param.user.nope}}]",
                Map.of("user", "{\"email\":\"a@b.com\"}"));

        assertEquals("missing=[]", result);
    }

    @Test
    void resolveParamFieldOnNonJsonYieldsEmpty() {

        var result = Placeholders.resolveParams(
                "field=[{{param.name.first}}]",
                Map.of("name", "plain string, not json"));

        assertEquals("field=[]", result);
    }

    @Test
    void resolveParamFieldMissingParamYieldsEmpty() {

        var result = Placeholders.resolveParams(
                "x=[{{param.nope.field}}]",
                Map.of("other", "{\"field\":\"ignored\"}"));

        assertEquals("x=[]", result);
    }

    @Test
    void resolveInputRendersFlatParamsAsJson() {

        var result = Placeholders.resolveParams(
                "Inputs:\n{{input}}",
                Map.of("name", "alice", "count", "5"));

        assertTrue(result.startsWith("Inputs:\n{"), "Expected pretty-JSON object, got: " + result);
        assertTrue(result.contains("\"name\" : \"alice\""), "Expected flat string value: " + result);
        assertTrue(result.contains("\"count\" : \"5\""), "Expected flat string value: " + result);
    }

    @Test
    void resolveInputUnescapesJsonStringValues() {

        var result = Placeholders.resolveParams(
                "{{input}}",
                Map.of("user", "{\"email\":\"a@b.com\",\"name\":\"Alice\"}"));

        assertTrue(result.contains("\"email\" : \"a@b.com\""),
                "Nested object should expand, not be escaped: " + result);
        assertTrue(result.contains("\"name\" : \"Alice\""), result);
        assertFalse(result.contains("\\\""), "Output should not contain escaped quotes: " + result);
    }

    @Test
    void resolveInputCoexistsWithParamPlaceholders() {

        var result = Placeholders.resolveParams(
                "Hello {{param.name}}. Full input:\n{{input}}",
                Map.of("name", "World", "role", "dev"));

        assertTrue(result.startsWith("Hello World. Full input:"), result);
        assertTrue(result.contains("\"name\" : \"World\""), result);
        assertTrue(result.contains("\"role\" : \"dev\""), result);
    }

    @Test
    void resolveInputWithEmptyParamsRendersEmptyObject() {

        var result = Placeholders.resolveParams("Inputs: {{input}}", Map.of());

        assertEquals("Inputs: { }", result);
    }

    @Test
    void missingInputPlaceholderIsUnchanged() {

        var result = Placeholders.resolveParams(
                "Hello {{param.name}}",
                Map.of("name", "Alice"));

        assertEquals("Hello Alice", result);
        assertFalse(result.contains("{"));
    }
}
