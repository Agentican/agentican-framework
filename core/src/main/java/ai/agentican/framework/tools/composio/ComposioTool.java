package ai.agentican.framework.tools.composio;

import ai.agentican.framework.tools.HitlType;
import ai.agentican.framework.tools.Tool;

import java.util.List;
import java.util.Map;

public record ComposioTool(
        String name,
        String displayName,
        String description,
        Map<String, Object> properties,
        List<String> required,
        HitlType hitlType,
        String toolkitSlug,
        String connectedAccountId,
        String version) implements Tool {

    public ComposioTool {

        if (name == null || name.isBlank())
            throw new IllegalArgumentException("Tool name is required");

        if (displayName == null || displayName.isBlank())
            displayName = name;

        if (description == null)
            description = "";

        if (properties == null)
            properties = Map.of();

        if (required == null)
            required = List.of();

        if (hitlType == null)
            hitlType = HitlType.NONE;
    }
}
