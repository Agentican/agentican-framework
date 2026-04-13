package ai.agentican.framework.tools.mcp;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.json.TypeRef;
import io.modelcontextprotocol.spec.McpClientTransport;
import io.modelcontextprotocol.spec.McpSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

class PatchedStreamableHttpTransport implements McpClientTransport {

    private static final Logger LOG = LoggerFactory.getLogger(PatchedStreamableHttpTransport.class);

    private static final ObjectMapper JACKSON = new ObjectMapper();

    private final String endpoint;
    private final HttpClient httpClient;
    private final McpJsonMapper jsonMapper;
    private final Map<String, String> customHeaders;
    private volatile String sessionId;
    private volatile Function<Mono<McpSchema.JSONRPCMessage>, Mono<McpSchema.JSONRPCMessage>> handler;
    private volatile Consumer<Throwable> exceptionHandler;

    PatchedStreamableHttpTransport(String endpoint, McpJsonMapper jsonMapper, Map<String, String> customHeaders) {
        this.endpoint = endpoint;
        this.jsonMapper = jsonMapper;
        this.customHeaders = customHeaders != null ? customHeaders : Map.of();
        this.httpClient = HttpClient.newHttpClient();
    }

    @Override
    public Mono<Void> connect(Function<Mono<McpSchema.JSONRPCMessage>, Mono<McpSchema.JSONRPCMessage>> handler) {
        this.handler = handler;
        return Mono.empty();
    }

    @Override
    public Mono<Void> sendMessage(McpSchema.JSONRPCMessage message) {
        return Mono.defer(() -> {
            var requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json, text/event-stream");

            if (sessionId != null)
                requestBuilder.header("Mcp-Session-Id", sessionId);

            customHeaders.forEach(requestBuilder::header);

            String body;
            try {
                body = jsonMapper.writeValueAsString(message);
            } catch (Exception e) {
                return Mono.<Void>error(e);
            }

            requestBuilder.POST(HttpRequest.BodyPublishers.ofString(body));

            LOG.debug("MCP sending: {}", body.substring(0, Math.min(200, body.length())));

            return Mono.fromFuture(
                httpClient.sendAsync(requestBuilder.build(), HttpResponse.BodyHandlers.ofString())
            ).flatMap(response -> {
                response.headers().firstValue("Mcp-Session-Id").ifPresent(id -> this.sessionId = id);

                int status = response.statusCode();

                // THE FIX: 202 Accepted = notification acknowledged, no body — complete immediately
                if (status == 202) {
                    LOG.debug("MCP 202 Accepted — completing immediately");
                    return Mono.<Void>empty();
                }

                if (status >= 200 && status < 300) {
                    String responseBody = response.body();
                    String contentType = response.headers().firstValue("Content-Type").orElse("");

                    if (responseBody != null && !responseBody.isBlank()) {
                        if (contentType.contains("text/event-stream"))
                            return parseSseResponse(responseBody);

                        try {
                            McpSchema.JSONRPCMessage responseMsg = deserializeMessage(responseBody);
                            return handler.apply(Mono.just(responseMsg)).then();
                        } catch (Exception e) {
                            LOG.error("Failed to parse MCP JSON response: {}", e.getMessage());
                            return Mono.<Void>error(e);
                        }
                    }
                    return Mono.<Void>empty();
                }

                String errBody = response.body();
                LOG.warn("MCP server returned HTTP {}: {}", status, errBody);
                return Mono.<Void>error(
                    new RuntimeException("MCP server returned HTTP " + status + ": " + errBody));
            });
        });
    }

    private Mono<Void> parseSseResponse(String sseBody) {
        Mono<Void> chain = Mono.empty();
        for (String line : sseBody.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("data:")) {
                String jsonData = trimmed.substring(5).trim();
                if (jsonData.isEmpty()) continue;
                try {
                    McpSchema.JSONRPCMessage msg = deserializeMessage(jsonData);
                    chain = chain.then(Mono.defer(() -> handler.apply(Mono.just(msg)).then()));
                } catch (Exception e) {
                    LOG.error("Failed to parse SSE data line: {}", e.getMessage());
                }
            }
        }
        return chain;
    }

    private McpSchema.JSONRPCMessage deserializeMessage(String json) throws Exception {
        JsonNode node = JACKSON.readTree(json);
        boolean hasId = node.has("id");
        boolean hasMethod = node.has("method");

        if (hasMethod && hasId)
            return jsonMapper.readValue(json, McpSchema.JSONRPCRequest.class);
        if (hasMethod)
            return jsonMapper.readValue(json, McpSchema.JSONRPCNotification.class);
        if (hasId)
            return jsonMapper.readValue(json, McpSchema.JSONRPCResponse.class);

        throw new IllegalArgumentException("Cannot determine JSON-RPC message type: " +
                json.substring(0, Math.min(100, json.length())));
    }

    @Override
    public void setExceptionHandler(Consumer<Throwable> handler) {
        this.exceptionHandler = handler;
    }

    @Override
    public Mono<Void> closeGracefully() {
        return Mono.empty();
    }

    @Override
    public <T> T unmarshalFrom(Object data, TypeRef<T> typeRef) {
        return jsonMapper.convertValue(data, typeRef);
    }

    @Override
    public List<String> protocolVersions() {
        return List.of("2025-06-18", "2024-11-05");
    }
}
