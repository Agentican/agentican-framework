package ai.agentican.framework.tools;

import java.util.List;
import java.util.Map;

public record ToolRecord(
        String name,
        String description,
        Map<String, Object> properties,
        List<String> required,
        HitlType hitlType) implements Tool {

    public ToolRecord {

        if (name == null || name.isBlank())
            throw new IllegalArgumentException("Tool name is required");

        if (description == null)
            description = "";

        if (properties == null)
            properties = Map.of();

        if (required == null)
            required = List.of();

        if (hitlType == null)
            hitlType = HitlType.NONE;
    }

    public ToolRecord(String name, String description, Map<String, Object> properties, List<String> required) {

        this(name, description, properties, required, HitlType.NONE);
    }

    public ToolRecord(String name, String description, Map<String, Object> properties) {

        this(name, description, properties, List.of(), HitlType.NONE);
    }
}
