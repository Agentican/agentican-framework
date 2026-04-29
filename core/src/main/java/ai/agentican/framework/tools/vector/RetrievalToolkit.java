package ai.agentican.framework.tools.vector;

import ai.agentican.framework.hitl.HitlType;
import ai.agentican.framework.vector.VectorIndexRegistry;
import ai.agentican.framework.tools.Tool;
import ai.agentican.framework.tools.ToolRecord;
import ai.agentican.framework.tools.Toolkit;
import ai.agentican.framework.util.Json;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public final class RetrievalToolkit implements Toolkit {

    public static final String SLUG        = "retrieval";

    public static final String TOOL_PREFIX = "search_";

    private static final Pattern VALID_KB_NAME =
            Pattern.compile("^[A-Za-z][A-Za-z0-9_-]{0,55}$");

    private final VectorIndexRegistry registry;
    private final List<Tool>            tools;

    public RetrievalToolkit(VectorIndexRegistry registry) {

        if (registry == null)
            throw new IllegalArgumentException("VectorIndexRegistry is required");

        this.registry = registry;

        var built = new ArrayList<Tool>();
        for (var name : registry.names()) {

            if (!VALID_KB_NAME.matcher(name).matches())
                throw new IllegalArgumentException(
                        "Knowledge-base name '" + name + "' must match "
                      + "[A-Za-z][A-Za-z0-9_-]{0,55} for use in tool names");

            built.add(buildTool(name, registry.get(name).description()));
        }
        this.tools = List.copyOf(built);
    }

    @Override public String displayName() { return "Retrieval"; }

    @Override public List<Tool> tools()   { return tools; }

    @Override
    public boolean handles(String toolName) {

        if (toolName == null || !toolName.startsWith(TOOL_PREFIX)) return false;

        return registry.contains(toolName.substring(TOOL_PREFIX.length()));
    }

    @Override
    public String execute(String toolName, Map<String, Object> arguments) {

        var kbName = toolName.substring(TOOL_PREFIX.length());
        var kb     = registry.get(kbName);
        if (kb == null)
            throw new IllegalStateException("Unknown vector index: " + kbName);

        var query = (String) arguments.get("query");
        var k     = arguments.get("k") instanceof Number n ? n.intValue() : 5;

        var hits = kb.retrieve(query == null ? "" : query, k).stream()
                .map(h -> Map.of(
                        "id",       (Object) h.id(),
                        "score",    (Object) h.score(),
                        "content",  (Object) h.content(),
                        "metadata", (Object) h.metadata()))
                .toList();

        try {
            return Json.writeValueAsString(Map.of("hits", hits));
        }
        catch (Exception _) {
            return "{\"hits\":[]}";
        }
    }

    private static Tool buildTool(String kbName, String description) {

        var trimmed = description == null ? "" : description.trim();

        var fullDescription =
                "Search the '" + kbName + "' vector index by semantic similarity. "
              + "Use this when you need information from this vector index to answer "
              + "the user's question."
              + (trimmed.isEmpty() ? "" : " Vector index scope: " + trimmed);

        return new ToolRecord(
                TOOL_PREFIX + kbName,
                fullDescription,
                Map.of(
                        "query", Map.of(
                                "type",        "string",
                                "description", "Natural-language query."),
                        "k",     Map.of(
                                "type",        "integer",
                                "description", "Number of hits to return (default 5).")),
                List.of("query"),
                HitlType.NONE);
    }
}
