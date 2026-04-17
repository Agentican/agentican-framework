package ai.agentican.framework.knowledge;

import ai.agentican.framework.tools.HitlType;
import ai.agentican.framework.tools.Tool;
import ai.agentican.framework.tools.ToolRecord;
import ai.agentican.framework.tools.Toolkit;
import ai.agentican.framework.util.Json;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class KnowledgeToolkit implements Toolkit {

    public static final String TOOL_NAME = "RECALL_KNOWLEDGE";

    private static final Tool RECALL_TOOL = new ToolRecord(
            TOOL_NAME,
            "Retrieve full details for one or more knowledge entries by id. Use this when an "
                    + "entry in the knowledge base index looks relevant to your task — it returns "
                    + "the entry's facts, descriptions, and tags so you can use them in your reasoning.",
            Map.of("entry_ids", Map.of(
                    "type", "array",
                    "items", Map.of("type", "string"),
                    "description", "List of knowledge entry ids to recall.")),
            List.of("entry_ids"),
            HitlType.NONE);

    private final KnowledgeStore store;

    public KnowledgeToolkit(KnowledgeStore store) {

        if (store == null)
            throw new IllegalArgumentException("KnowledgeStore is required");

        this.store = store;
    }

    @Override
    public List<Tool> tools() {

        return List.of(RECALL_TOOL);
    }

    @Override
    public boolean handles(String toolName) {

        return TOOL_NAME.equals(toolName);
    }

    @Override
    @SuppressWarnings("unchecked")
    public String execute(String toolName, Map<String, Object> arguments) {

        var rawIds = arguments.get("entry_ids");
        var ids = rawIds instanceof List<?> list ? (List<String>) list : List.<String>of();

        var recalled = new ArrayList<Map<String, Object>>();

        for (var id : ids) {

            var entry = store.get(id);

            if (entry == null) continue;

            var factsJson = entry.facts().stream()
                    .map(f -> (Map<String, Object>) Map.of(
                            "id", f.id(),
                            "name", f.name(),
                            "content", f.content(),
                            "tags", f.tags()))
                    .toList();

            recalled.add(Map.of(
                    "id", entry.id(),
                    "name", entry.name(),
                    "description", entry.description(),
                    "facts", factsJson));
        }

        try {
            return Json.writeValueAsString(Map.of("entries", recalled));
        }
        catch (Exception _) {
            return "{\"entries\":[]}";
        }
    }
}
