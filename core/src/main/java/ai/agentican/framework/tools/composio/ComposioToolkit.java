package ai.agentican.framework.tools.composio;

import ai.agentican.framework.hitl.HitlType;
import ai.agentican.framework.tools.Tool;
import ai.agentican.framework.tools.Toolkit;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.stream.StreamSupport;

public class ComposioToolkit implements Toolkit {

    private static final Logger LOG = LoggerFactory.getLogger(ComposioToolkit.class);
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Duration EXECUTE_TIMEOUT = Duration.ofSeconds(90);
    private static final String BASE_URL = "https://backend.composio.dev";

    private final String apiKey;
    private final String userId;
    private final String toolkitSlug;
    private final String toolkitDisplayName;
    private final String connectedAccountId;
    private final HttpClient httpClient;

    private final Map<String, ComposioTool> toolsBySlug;

    ComposioToolkit(String apiKey, String userId, String toolkitSlug, String toolkitDisplayName,
                    String connectedAccountId, HttpClient httpClient, Map<String, HitlType> hitlOverrides) {

        this.apiKey = apiKey;
        this.userId = userId;
        this.toolkitSlug = toolkitSlug;
        this.toolkitDisplayName = toolkitDisplayName;
        this.connectedAccountId = connectedAccountId;
        this.httpClient = httpClient;

        var tools = new LinkedHashMap<String, ComposioTool>();

        fetchTools(tools, hitlOverrides != null ? hitlOverrides : Map.of());

        this.toolsBySlug = Collections.unmodifiableMap(tools);

        LOG.info("Toolkit '{}': {} tools loaded", toolkitSlug, toolsBySlug.size());
    }

    ComposioToolkit(String apiKey, String userId, String toolkitSlug, String toolkitDisplayName,
                    String connectedAccountId, HttpClient httpClient) {

        this(apiKey, userId, toolkitSlug, toolkitDisplayName, connectedAccountId, httpClient, Map.of());
    }

    @Override
    public String displayName() {

        return toolkitDisplayName;
    }

    public String slug() {

        return toolkitSlug;
    }

    @Override
    public List<Tool> tools() {

        return List.copyOf(toolsBySlug.values());
    }

    @Override
    public boolean handles(String toolName) {

        return toolsBySlug.containsKey(toolName);
    }

    @Override
    public String execute(String toolName, Map<String, Object> arguments) throws Exception {

        var tool = toolsBySlug.get(toolName);

        if (tool == null)
            return "{\"successful\":false,\"error\":\"Unknown tool: " + toolName + "\"}";

        var body = new LinkedHashMap<String, Object>();

        body.put("arguments", arguments != null ? arguments : Map.of());

        if (connectedAccountId != null)
            body.put("connected_account_id", connectedAccountId);

        body.put("user_id", userId);

        if (tool.version() != null)
            body.put("version", tool.version());

        LOG.info("Executing Composio tool '{}' (toolkit: {})", toolName, toolkitSlug);

        var request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/api/v3/tools/execute/" + toolName))
                .header("x-api-key", apiKey)
                .header("Content-Type", "application/json")
                .timeout(EXECUTE_TIMEOUT)
                .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body)))
                .build();

        var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() >= 400) {

            var err = truncateError(response.body());

            LOG.error("Composio tool '{}' failed (HTTP {}): {}", toolName, response.statusCode(), err);

            return "{\"successful\":false,\"error\":\"HTTP " + response.statusCode() + ": " + escapeJson(err) + "\"}";
        }

        return response.body();
    }

    private void fetchTools(Map<String, ComposioTool> tools, Map<String, HitlType> hitlOverrides) {

        try {

            String cursor = null;

            do {

                var url = BASE_URL + "/api/v3.1/tools?toolkit_slug=" + toolkitSlug + "&limit=100"
                        + (cursor != null ? "&cursor=" + cursor : "");

                var root = getJson(url);

                StreamSupport.stream(root.path("items").spliterator(), false)
                        .filter(tool -> !tool.path("slug").asText("").isEmpty())
                        .forEach(tool -> {

                            var slug = tool.path("slug").asText("");
                            var description = tool.path("description").asText("");
                            var properties = extractProperties(tool.path("input_parameters"));
                            var required = extractRequired(tool.path("input_parameters"));
                            var hitlType = hitlOverrides.getOrDefault(slug, HitlType.NONE);

                            tools.put(slug, new ComposioTool(
                                    slug,
                                    tool.path("name").asText(slug),
                                    description,
                                    properties,
                                    required,
                                    hitlType,
                                    toolkitSlug,
                                    connectedAccountId,
                                    tool.path("version").asText(null)));
                        });

                cursor = root.path("next_cursor").isNull() ? null : root.path("next_cursor").asText(null);
            }
            while (cursor != null);
        }
        catch (Exception e) {

            LOG.warn("Failed to fetch tools for toolkit '{}': {}", toolkitSlug, e.getMessage());
        }
    }

    private JsonNode getJson(String url) throws IOException, InterruptedException {

        var request = HttpRequest.newBuilder().uri(URI.create(url)).header("x-api-key", apiKey).GET().build();

        var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() >= 400)
            throw new IOException("HTTP " + response.statusCode() + ": " + truncateError(response.body()));

        return JSON.readTree(response.body());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractProperties(JsonNode inputParameters) {

        if (inputParameters == null || inputParameters.isMissingNode()) return Map.of();

        var props = inputParameters.path("properties");

        if (props.isMissingNode() || !props.isObject()) return Map.of();

        try {

            return JSON.convertValue(props, Map.class);
        }
        catch (Exception e) {
            return Map.of();
        }
    }

    @SuppressWarnings("unchecked")
    private List<String> extractRequired(JsonNode inputParameters) {

        if (inputParameters == null || inputParameters.isMissingNode()) return List.of();

        var req = inputParameters.path("required");

        if (req.isMissingNode() || !req.isArray()) return List.of();

        try {

            return JSON.convertValue(req, List.class);
        }
        catch (Exception e) {
            return List.of();
        }
    }

    private static String truncateError(String msg) {

        if (msg == null) return "";

        return msg.length() > 200 ? msg.substring(0, 200) + "..." : msg;
    }

    private static String escapeJson(String s) {

        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n");
    }
}
