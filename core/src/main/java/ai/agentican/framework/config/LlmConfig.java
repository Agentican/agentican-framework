package ai.agentican.framework.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record LlmConfig(
        String name,
        String provider,
        String model,
        String apiKey,
        String secretKey,
        String region,
        long maxTokens,
        Double temperature,
        String baseUrl) {

    public static final String DEFAULT = "default";

    public LlmConfig {

        if (name == null || name.isBlank()) name = DEFAULT;
        if (provider == null) provider = "anthropic";
        if (maxTokens == 0) maxTokens = 16384;

        if ("bedrock".equals(provider)) {

            if (model == null || model.isBlank())
                throw new IllegalArgumentException(
                        "model is required for provider 'bedrock' (e.g. 'anthropic.claude-sonnet-4-5-20250929-v1:0')");

            if (apiKey != null && !apiKey.isBlank() && (secretKey == null || secretKey.isBlank()))
                throw new IllegalArgumentException(
                        "secretKey is required when apiKey is set for provider 'bedrock'");

        } else {

            if (apiKey == null || apiKey.isBlank())
                throw new IllegalArgumentException("LLM API key is required");

            if (model == null) model = "claude-sonnet-4-5";
        }

        if ("openai-compatible".equals(provider) && (baseUrl == null || baseUrl.isBlank()))
            throw new IllegalArgumentException(
                    "baseUrl is required when provider is 'openai-compatible'");
    }

    public static LlmConfigBuilder builder() {

        return new LlmConfigBuilder();
    }

    public static class LlmConfigBuilder {

        private String name;
        private String provider;
        private String model;
        private String apiKey;
        private String secretKey;
        private String region;

        private long maxTokens;

        private Double temperature;
        private String baseUrl;

        public LlmConfigBuilder name(String name) { this.name = name; return this; }
        public LlmConfigBuilder provider(String provider) { this.provider = provider; return this; }
        public LlmConfigBuilder model(String model) { this.model = model; return this; }
        public LlmConfigBuilder apiKey(String apiKey) { this.apiKey = apiKey; return this; }
        public LlmConfigBuilder secretKey(String secretKey) { this.secretKey = secretKey; return this; }
        public LlmConfigBuilder region(String region) { this.region = region; return this; }
        public LlmConfigBuilder maxTokens(long maxTokens) { this.maxTokens = maxTokens; return this; }
        public LlmConfigBuilder temperature(Double temperature) { this.temperature = temperature; return this; }
        public LlmConfigBuilder baseUrl(String baseUrl) { this.baseUrl = baseUrl; return this; }

        public LlmConfig build() {

            return new LlmConfig(name, provider, model, apiKey, secretKey, region, maxTokens, temperature, baseUrl);
        }
    }
}
