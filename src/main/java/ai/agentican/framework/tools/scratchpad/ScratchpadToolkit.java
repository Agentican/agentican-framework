package ai.agentican.framework.tools.scratchpad;

import ai.agentican.framework.tools.Tool;
import ai.agentican.framework.tools.ToolDefinition;
import ai.agentican.framework.tools.ToolRecord;
import ai.agentican.framework.tools.Toolkit;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ScratchpadToolkit implements Toolkit {

    private static final ObjectMapper JSON = new ObjectMapper();

    private static final String STORE = "store";
    private static final String RECALL = "recall";
    private static final String RECALL_ALL = "recall_all";

    public static final List<String> TOOL_NAMES = List.of(STORE, RECALL, RECALL_ALL);

    private static final List<Tool> TOOLS = List.of(
            new ToolRecord(STORE,
                    "Store information for later use. Creates or replaces an entry. "
                            + "Use when you need data to survive across turns.",
                    Map.of("id", Map.of("type", "string", "description", "Unique key for this entry"),
                            "description", Map.of("type", "string", "description", "Brief summary of what is stored"),
                            "details", Map.of("type", "string", "description", "The full content to store"))),
            new ToolRecord(RECALL,
                    "Retrieve a previously stored entry by its key.",
                    Map.of("id", Map.of("type", "string", "description", "Key of the entry to retrieve"))),
            new ToolRecord(RECALL_ALL,
                    "List all stored entries with their keys, descriptions, and details.",
                    Map.of()));

    public static List<ToolDefinition> definitions() {

        return TOOLS.stream().map(Tool::toDefinition).toList();
    }

    @Override
    public List<Tool> tools() {

        return TOOLS;
    }

    private final Map<String, ScratchpadEntry> entries = new ConcurrentHashMap<>();

    public ScratchpadEntry store(String key, String description, String details) {

        var entry = new ScratchpadEntry(key, description, details);

        entries.put(key, entry);

        return entry;
    }

    public ScratchpadEntry get(String key) {
        return entries.get(key);
    }

    public List<String> keys() {
        return List.copyOf(entries.keySet());
    }

    public List<ScratchpadEntry> entries() {
        return List.copyOf(entries.values());
    }

    @Override
    public boolean handles(String toolName) {

        return STORE.equals(toolName) || RECALL.equals(toolName) || RECALL_ALL.equals(toolName);
    }

    @Override
    public String execute(String toolName, Map<String, Object> arguments) {

        try {

            return switch (toolName) {

                case STORE -> {

                    var id = String.valueOf(arguments.get("id"));
                    var desc = String.valueOf(arguments.get("description"));
                    var details = String.valueOf(arguments.get("details"));

                    store(id, desc, details);

                    yield JSON.writeValueAsString(Map.of("stored", id));
                }

                case RECALL -> {

                    var id = String.valueOf(arguments.get("id"));
                    var entry = get(id);

                    if (entry == null)
                        yield JSON.writeValueAsString(Map.of("error", "No entry found for key: " + id));

                    yield JSON.writeValueAsString(Map.of(
                            "key", entry.key(),
                            "description", entry.description(),
                            "details", entry.details()));
                }

                case RECALL_ALL -> {

                    var all = entries.values().stream()
                            .map(e -> Map.of("key", e.key(), "description", e.description(), "details", e.details()))
                            .toList();

                    yield JSON.writeValueAsString(Map.of("entries", all));
                }

                default -> JSON.writeValueAsString(Map.of("error", "Unknown scratchpad tool: " + toolName));
            };
        }
        catch (Exception e) {

            return "{\"error\":\"" + e.getMessage() + "\"}";
        }
    }

}
