package ai.agentican.framework.orchestration.planning;

import ai.agentican.framework.tools.ToolDefinition;
import ai.agentican.framework.util.Json;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record ToolView(String name, String description, String schema) {

    public static ToolView from(ToolDefinition def) {

        return new ToolView(def.name(), def.description(), formatSchema(def));
    }

    public static List<ToolView> fromAll(List<ToolDefinition> defs) {

        return defs.stream().map(ToolView::from).toList();
    }

    private static String formatSchema(ToolDefinition def) {

        try {

            var schema = new LinkedHashMap<String, Object>();

            schema.put("properties", def.properties());

            if (!def.required().isEmpty())
                schema.put("required", def.required());

            return Json.writeValueAsString(schema);
        }
        catch (Exception e) {

            return "{}";
        }
    }
}
