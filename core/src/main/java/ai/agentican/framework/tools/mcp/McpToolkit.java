package ai.agentican.framework.tools.mcp;

import ai.agentican.framework.config.McpConfig;
import ai.agentican.framework.tools.Tool;
import ai.agentican.framework.tools.ToolRecord;
import ai.agentican.framework.tools.Toolkit;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import io.modelcontextprotocol.json.jackson3.JacksonMcpJsonMapperSupplier;
import io.modelcontextprotocol.spec.McpSchema;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

public class McpToolkit implements Toolkit, AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(McpToolkit.class);

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String name;
    private final McpSyncClient client;
    private final ReentrantLock executeLock = new ReentrantLock();

    private final Map<String, Tool> toolsByName;

    public static McpToolkit of(McpConfig config) {

        return new McpToolkit(config);
    }

    @SuppressWarnings("unchecked")
    public McpToolkit(McpConfig config) {

        this.name = config.name();

        var url = buildUrl(config.url(), config.queryParams());

        this.client = tryConnect(config.name(), url, config.headers());

        var mutableTools = new LinkedHashMap<String, Tool>();

        if (client != null) {

            try {

                for (var tool : client.listTools().tools()) {

                    var toolName = tool.name();

                    Map<String, Object> props = null;

                    var schemaMap = MAPPER.convertValue(tool.inputSchema(), Map.class);

                    if (schemaMap != null && schemaMap.get("properties") instanceof Map) {

                        props = (Map<String, Object>) schemaMap.get("properties");
                    }

                    mutableTools.put(toolName, new ToolRecord(toolName, tool.description(), props));
                }
            }
            catch (Exception e) {

                LOG.warn("Failed to list tools from MCP endpoint '{}': {}", name, e.getMessage());
            }
        }

        this.toolsByName = Collections.unmodifiableMap(mutableTools);
    }

    @Override
    public List<Tool> tools() {

        return List.copyOf(toolsByName.values());
    }

    @Override
    public boolean handles(String toolName) {

        return toolsByName.containsKey(toolName);
    }

    @Override
    public String execute(String toolName, Map<String, Object> args) throws Exception {

        executeLock.lock();

        try {

            if (client == null)
                return "{\"error\":\"MCP endpoint '" + name + "' not connected\"}";

            var result = client.callTool(new McpSchema.CallToolRequest(toolName, args));

            return MAPPER.writeValueAsString(result);
        }
        finally {
            executeLock.unlock();
        }
    }

    @Override
    public void close() {

        if (client != null) {

            try {

                client.close();
            }
            catch (Exception e) {

                LOG.debug("Error closing MCP client '{}': {}", name, e.getMessage());
            }
        }
    }

    private static McpSyncClient tryConnect(String name, String url, Map<String, String> headers) {

        try {

            var transport = new PatchedStreamableHttpTransport(url, new JacksonMcpJsonMapperSupplier().get(), headers);

            var client = McpClient.sync(transport).clientInfo(new McpSchema.Implementation("agentican-framework", "1.0")).build();

            client.initialize();

            LOG.info("Connected to MCP endpoint '{}' via Streamable HTTP", name);

            return client;
        }
        catch (Exception e) {

            LOG.debug("Streamable HTTP failed for '{}': {}", name, e.getMessage());
        }

        try {

            var sseBuilder = HttpClientSseClientTransport.builder(url);

            if (headers != null && !headers.isEmpty())
                sseBuilder.customizeRequest(req -> headers.forEach(req::header));

            var client = McpClient.sync(sseBuilder.build()).clientInfo(new McpSchema.Implementation("agentican-framework", "1.0")).build();

            client.initialize();

            LOG.info("Connected to MCP endpoint '{}' via SSE", name);

            return client;
        }
        catch (Exception e) {

            LOG.error("Failed to connect to MCP endpoint '{}' at {}", name, url, e);
        }

        return null;
    }

    private static String buildUrl(String baseUrl, Map<String, String> queryParams) {

        if (queryParams == null || queryParams.isEmpty()) return baseUrl;

        var sb = new StringBuilder(baseUrl);

        sb.append(baseUrl.contains("?") ? "&" : "?");

        var first = true;

        for (var entry : queryParams.entrySet()) {

            if (!first) sb.append("&");

            sb.append(java.net.URLEncoder.encode(entry.getKey(), java.nio.charset.StandardCharsets.UTF_8));
            sb.append("=");
            sb.append(java.net.URLEncoder.encode(entry.getValue(), java.nio.charset.StandardCharsets.UTF_8));

            first = false;
        }

        return sb.toString();
    }
}
