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

    public enum Scope { LOCAL, SHARED }

    private static final ObjectMapper JSON = new ObjectMapper();

    public static final String STORE = "store";
    public static final String RECALL = "recall";
    public static final String RECALL_ALL = "recall_all";
    public static final String STORE_SHARED = "store_shared";
    public static final String RECALL_SHARED = "recall_shared";
    public static final String RECALL_ALL_SHARED = "recall_all_shared";

    public static final List<String> LOCAL_TOOL_NAMES = List.of(STORE, RECALL, RECALL_ALL);
    public static final List<String> SHARED_TOOL_NAMES = List.of(STORE_SHARED, RECALL_SHARED, RECALL_ALL_SHARED);

    private static final List<Tool> LOCAL_TOOLS = List.of(
            new ToolRecord(STORE,
                    "Save a value to the LOCAL scratchpad so a later turn can `recall` it to feed into another tool call. "
                            + "Call this ONLY when a future turn will need the value as input to a tool. "
                            + "Do NOT use this as a scratch buffer to organize notes before writing your final output — write the output directly. "
                            + "Do NOT use this to save content that is already part of your final output text.",
                    Map.of("id", Map.of("type", "string", "description", "Unique key for this entry"),
                            "description", Map.of("type", "string", "description", "Brief summary of what is stored"),
                            "details", Map.of("type", "string", "description", "The full content to store"))),
            new ToolRecord(RECALL,
                    "Retrieve a LOCAL scratchpad entry by its key. Tool results from your prior turns "
                            + "in this step are automatically stored here. "
                            + "PRECONDITION: `id` MUST exactly match a <key> listed in the <local><index> block of your user message. "
                            + "Do NOT invent or guess keys — if the key is not in the index, it does not exist and this call will fail.",
                    Map.of("id", Map.of("type", "string",
                            "description", "Key of the entry to retrieve. Must be copied verbatim from a <key> in <local><index>."))),
            new ToolRecord(RECALL_ALL,
                    "List every entry in your LOCAL scratchpad with keys, descriptions, and details.",
                    Map.of()));

    private static final List<Tool> SHARED_TOOLS = List.of(
            new ToolRecord(STORE_SHARED,
                    "Save a value to the SHARED scratchpad so another agent's tool call can `recall_shared` it later in the task. "
                            + "Call this ONLY when your task instructions direct you to, so a downstream agent can feed the value into a tool call. "
                            + "Do NOT use this as a scratch buffer for your own notes. Your step output is already passed downstream via `{{step.<name>.output}}` placeholders; do not duplicate it here.",
                    Map.of("id", Map.of("type", "string", "description", "Unique key for this entry"),
                            "description", Map.of("type", "string", "description", "Brief summary of what is stored"),
                            "details", Map.of("type", "string", "description", "The full content to store"))),
            new ToolRecord(RECALL_SHARED,
                    "Retrieve a SHARED scratchpad entry by its key (written by any step in the task). "
                            + "PRECONDITION: `id` MUST exactly match a <key> listed in the <shared><index> block of your user message. "
                            + "Do NOT invent or guess keys — if the key is not in the index, it does not exist and this call will fail.",
                    Map.of("id", Map.of("type", "string",
                            "description", "Key of the entry to retrieve. Must be copied verbatim from a <key> in <shared><index>."))),
            new ToolRecord(RECALL_ALL_SHARED,
                    "List every entry in the SHARED scratchpad with keys, descriptions, and details.",
                    Map.of()));

    public static List<ToolDefinition> localDefinitions() {
        return LOCAL_TOOLS.stream().map(Tool::toDefinition).toList();
    }

    public static List<ToolDefinition> sharedDefinitions() {
        return SHARED_TOOLS.stream().map(Tool::toDefinition).toList();
    }

    private final Scope scope;
    private final Map<String, ScratchpadEntry> entries = new ConcurrentHashMap<>();

    public ScratchpadToolkit() {
        this(Scope.LOCAL);
    }

    public ScratchpadToolkit(Scope scope) {
        this.scope = scope;
    }

    public Scope scope() {
        return scope;
    }

    @Override
    public List<Tool> tools() {
        return scope == Scope.LOCAL ? LOCAL_TOOLS : SHARED_TOOLS;
    }

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

        if (scope == Scope.LOCAL)
            return STORE.equals(toolName) || RECALL.equals(toolName) || RECALL_ALL.equals(toolName);

        return STORE_SHARED.equals(toolName) || RECALL_SHARED.equals(toolName) || RECALL_ALL_SHARED.equals(toolName);
    }

    @Override
    public String execute(String toolName, Map<String, Object> arguments) {

        try {

            return switch (toolName) {

                case STORE, STORE_SHARED -> {

                    var id = String.valueOf(arguments.get("id"));
                    var desc = String.valueOf(arguments.get("description"));
                    var details = String.valueOf(arguments.get("details"));

                    store(id, desc, details);

                    yield JSON.writeValueAsString(Map.of("stored", id));
                }

                case RECALL, RECALL_SHARED -> {

                    var id = String.valueOf(arguments.get("id"));
                    var entry = get(id);

                    if (entry == null)
                        yield JSON.writeValueAsString(Map.of("error", "No entry found for key: " + id));

                    yield JSON.writeValueAsString(Map.of(
                            "key", entry.key(),
                            "description", entry.description(),
                            "details", entry.details()));
                }

                case RECALL_ALL, RECALL_ALL_SHARED -> {

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
