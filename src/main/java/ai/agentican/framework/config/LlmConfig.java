package ai.agentican.framework.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record LlmConfig(
        String name,
        String provider,
        String model,
        String apiKey,
        long maxTokens) {

    public static final String DEFAULT = "default";

    public LlmConfig {

        if (apiKey == null || apiKey.isBlank())
            throw new IllegalArgumentException("LLM API key is required");

        if (name == null || name.isBlank()) name = DEFAULT;
        if (provider == null) provider = "anthropic";
        if (model == null) model = "claude-sonnet-4-5";
        if (maxTokens == 0) maxTokens = 16384;
    }

    public static LlmConfig of(String name, String provider, String model, String apiKey, long maxTokens) {

        return new LlmConfig(name, provider, model, apiKey, maxTokens);
    }

    public static LlmConfigBuilder builder() {

        return new LlmConfigBuilder();
    }

    public static class LlmConfigBuilder {

        private String name;
        private String provider;
        private String model;
        private String apiKey;

        private long maxTokens;

        public LlmConfigBuilder name(String name) { this.name = name; return this; }
        public LlmConfigBuilder provider(String provider) { this.provider = provider; return this; }
        public LlmConfigBuilder model(String model) { this.model = model; return this; }
        public LlmConfigBuilder apiKey(String apiKey) { this.apiKey = apiKey; return this; }
        public LlmConfigBuilder maxTokens(long maxTokens) { this.maxTokens = maxTokens; return this; }

        public LlmConfig build() {

            return LlmConfig.of(name, provider, model, apiKey, maxTokens);
        }
    }
}
