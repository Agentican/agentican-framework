package ai.agentican.framework.embeddings.provider;

import ai.agentican.framework.embeddings.EmbeddingClient;
import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.models.embeddings.EmbeddingCreateParams;
import com.openai.models.embeddings.EmbeddingModel;

import java.util.List;
import java.util.Map;

public final class OpenAiEmbeddingClient implements EmbeddingClient {

    private static final Map<String, Integer> NATIVE_DIMENSIONS = Map.of(
            "text-embedding-3-small", 1536,
            "text-embedding-3-large", 3072,
            "text-embedding-ada-002", 1536);

    private final OpenAIClient client;
    private final String model;
    private final int dimensions;
    private final boolean truncateDimensions;

    private OpenAiEmbeddingClient(OpenAIClient client, String model, int dimensions, boolean truncate) {

        this.client = client;
        this.model = model;
        this.dimensions = dimensions;
        this.truncateDimensions = truncate;
    }

    public static Builder builder() {

        return new Builder();
    }

    @Override
    public List<float[]> embed(List<String> texts) {

        if (texts == null || texts.isEmpty()) return List.of();

        var paramsBuilder = EmbeddingCreateParams.builder()
                .input(EmbeddingCreateParams.Input.ofArrayOfStrings(texts))
                .model(EmbeddingModel.of(model));

        if (truncateDimensions) paramsBuilder.dimensions(dimensions);

        var response = client.embeddings().create(paramsBuilder.build());

        return response.data().stream().map(e -> {

            var floats = e.embedding();

            var arr = new float[floats.size()];

            for (var i = 0; i < floats.size(); i++) arr[i] = floats.get(i);

            return arr;

        }).toList();
    }

    @Override public int dimensions() { return dimensions; }

    @Override public String modelId() { return "openai:" + model; }

    public static final class Builder {

        private String apiKey;
        private String baseUrl;
        private String model = "text-embedding-3-small";
        private Integer dimensions;

        public Builder apiKey(String apiKey) { this.apiKey = apiKey; return this; }
        public Builder baseUrl(String baseUrl) { this.baseUrl = baseUrl; return this; }
        public Builder model(String model) { this.model = model; return this; }
        public Builder dimensions(int dimensions) { this.dimensions = dimensions; return this; }

        public OpenAiEmbeddingClient build() {

            if (apiKey == null || apiKey.isBlank())
                throw new IllegalArgumentException("OpenAI apiKey is required");

            if (model == null || model.isBlank())
                throw new IllegalArgumentException("OpenAI embedding model is required");

            var clientBuilder = OpenAIOkHttpClient.builder().apiKey(apiKey);

            if (baseUrl != null && !baseUrl.isBlank()) clientBuilder.baseUrl(baseUrl);

            int resolvedDimensions;
            boolean truncate;

            if (dimensions != null) {

                resolvedDimensions = dimensions;
                truncate           = true;
            }
            else {

                var native_ = NATIVE_DIMENSIONS.get(model);

                if (native_ == null)
                    throw new IllegalArgumentException(
                            "Unknown native dimensions for model '" + model
                          + "'. Call .dimensions(int) explicitly or use a known model: "
                          + NATIVE_DIMENSIONS.keySet());

                resolvedDimensions = native_;
                truncate = false;
            }

            return new OpenAiEmbeddingClient(clientBuilder.build(), model, resolvedDimensions, truncate);
        }
    }
}
