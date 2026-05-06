package ai.agentican.framework.embeddings.provider;

import ai.agentican.framework.embeddings.EmbeddingClient;
import ai.agentican.framework.util.Json;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class VoyageEmbeddingClient implements EmbeddingClient {

    private static final Map<String, Integer> NATIVE_DIMENSIONS = Map.of(
            "voyage-3-large", 1024,
            "voyage-3", 1024,
            "voyage-3-lite", 512,
            "voyage-code-3", 1024,
            "voyage-finance-2", 1024,
            "voyage-law-2", 1024);

    private static final String DEFAULT_BASE_URL = "https://api.voyageai.com/v1/embeddings";

    private final HttpClient httpClient;
    private final String     apiKey;
    private final String     baseUrl;
    private final String     model;
    private final int        dimensions;
    private final Duration   timeout;

    private VoyageEmbeddingClient(HttpClient httpClient, String apiKey, String baseUrl,
                                  String model, int dimensions, Duration timeout) {

        this.httpClient = httpClient;
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
        this.model = model;
        this.dimensions = dimensions;
        this.timeout = timeout;
    }

    public static Builder builder() {

        return new Builder();
    }

    @Override
    public List<float[]> embed(List<String> texts) {

        if (texts == null || texts.isEmpty()) return List.of();

        try {

            var body = Json.writeValueAsString(Map.of(
                    "input", texts,
                    "model", model));

            var request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl))
                    .timeout(timeout)
                    .header("Content-Type",  "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() < 200 || response.statusCode() >= 300)
                throw new RuntimeException("Voyage API error " + response.statusCode() + ": " + response.body());

            return parseResponse(response.body());
        }
        catch (InterruptedException e) {

            Thread.currentThread().interrupt();

            throw new RuntimeException("Voyage embed interrupted", e);
        }
        catch (IOException e) {

            throw new RuntimeException("Voyage embed failed: " + e.getMessage(), e);
        }
    }

    @Override
    public int dimensions() {

        return dimensions;
    }

    @Override
    public String modelId() {

        return "voyage:" + model;
    }

    private static List<float[]> parseResponse(String body) {

        try {

            var parsed = Json.mapper().readTree(body);
            var data = parsed.get("data");

            if (data == null || !data.isArray())
                throw new RuntimeException("Voyage response missing 'data' array: " + body);

            var vectors = new ArrayList<float[]>(data.size());

            for (var item : data) {

                var emb = item.get("embedding");

                if (emb == null || !emb.isArray())
                    throw new RuntimeException("Voyage response item missing 'embedding' array");

                var arr = new float[emb.size()];

                for (var i = 0; i < emb.size(); i++) arr[i] = (float) emb.get(i).asDouble();

                vectors.add(arr);
            }

            return vectors;
        }
        catch (Exception e) {

            throw new RuntimeException("Voyage failed to parse response: " + e.getMessage(), e);
        }
    }

    public static final class Builder {

        private String apiKey;
        private String baseUrl = DEFAULT_BASE_URL;
        private String model = "voyage-3";
        private Integer dimensions;
        private Duration timeout = Duration.ofSeconds(60);
        private HttpClient httpClient;

        public Builder apiKey(String apiKey)         { this.apiKey = apiKey; return this; }
        public Builder baseUrl(String baseUrl)       { this.baseUrl = baseUrl; return this; }
        public Builder model(String model)           { this.model = model; return this; }
        public Builder dimensions(int dimensions)    { this.dimensions = dimensions; return this; }
        public Builder timeout(Duration timeout)     { this.timeout = timeout; return this; }
        public Builder httpClient(HttpClient client) { this.httpClient = client; return this; }

        public VoyageEmbeddingClient build() {

            if (apiKey == null || apiKey.isBlank())
                throw new IllegalArgumentException("Voyage apiKey is required");

            if (model == null || model.isBlank())
                throw new IllegalArgumentException("Voyage model is required");

            int resolvedDim;

            if (dimensions != null) {

                resolvedDim = dimensions;
            }
            else {

                var native_ = NATIVE_DIMENSIONS.get(model);

                if (native_ == null)
                    throw new IllegalArgumentException(
                            "Unknown native dimensions for Voyage model '" + model
                          + "'. Call .dimensions(int) explicitly or use a known model: "
                          + NATIVE_DIMENSIONS.keySet());

                resolvedDim = native_;
            }

            var client = httpClient != null ? httpClient
                    : HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

            return new VoyageEmbeddingClient(client, apiKey, baseUrl, model, resolvedDim, timeout);
        }
    }
}
