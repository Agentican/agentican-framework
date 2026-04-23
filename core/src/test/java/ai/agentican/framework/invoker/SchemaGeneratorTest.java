package ai.agentican.framework.invoker;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SchemaGeneratorTest {

    record Annotated(
            @JsonPropertyDescription("Full name of the thing") String name,
            @JsonPropertyDescription("How many there are") int count) {}

    @Test
    void jsonPropertyDescriptionPropagatesToSchema() {

        var schema = SchemaGenerator.schemaFor(Annotated.class);

        assertNotNull(schema);

        var json = schema.toString();

        assertTrue(json.contains("Full name of the thing"),
                "Expected @JsonPropertyDescription on 'name' to appear in schema, got: " + json);
        assertTrue(json.contains("How many there are"),
                "Expected @JsonPropertyDescription on 'count' to appear in schema, got: " + json);
    }

    @Test
    void objectSchemaHasAdditionalPropertiesFalse() {

        var schema = SchemaGenerator.schemaFor(Annotated.class);

        assertEquals(Boolean.FALSE, schema.path("additionalProperties").asBoolean(true),
                "Anthropic rejects schemas where 'object' types don't explicitly set additionalProperties=false");
    }

    @Test
    void allRecordComponentsMarkedRequired() {

        var schema = SchemaGenerator.schemaFor(Annotated.class);
        var required = schema.path("required");

        assertTrue(required.isArray() && required.size() == 2,
                "Expected all record components to be required, got: " + required);

        var names = java.util.stream.StreamSupport.stream(required.spliterator(), false)
                .map(com.fasterxml.jackson.databind.JsonNode::asText)
                .toList();

        assertTrue(names.contains("name") && names.contains("count"),
                "Expected 'name' and 'count' in required[], got: " + names);
    }
}
