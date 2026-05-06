package ai.agentican.framework.llm;

import com.fasterxml.jackson.databind.JsonNode;

public record StructuredOutput(String name, JsonNode schema, boolean strict) {

    public StructuredOutput {

        if (schema == null) throw new IllegalArgumentException("schema is required");

        if (name == null || name.isBlank()) name = "response";
    }

    public StructuredOutput(String name, JsonNode schema) {

        this(name, schema, true);
    }
}
