package ai.agentican.framework.tools;

import java.util.List;
import java.util.Map;

public record ToolDefinition(
        String name,
        String description,
        Map<String, Object> properties,
        List<String> required) {

    public ToolDefinition {

        if (name == null || name.isBlank())
            throw new IllegalArgumentException("Tool name is required");

        if (description == null || description.isBlank())
            throw new IllegalArgumentException("Tool description is required");

        if (properties == null)
            properties = Map.of();

        if (required == null)
            required = List.of();
    }

    public ToolDefinition(String name, String description, Map<String, Object> properties) {

        this(name, description, properties, List.of());
    }
}
