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
}
