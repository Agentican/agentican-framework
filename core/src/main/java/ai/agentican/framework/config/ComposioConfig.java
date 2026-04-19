package ai.agentican.framework.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ComposioConfig(
        String apiKey,
        String userId) {

    public ComposioConfig {

        if (apiKey == null || apiKey.isBlank())
            throw new IllegalArgumentException("Composio API key is required");

        if (userId == null || userId.isBlank())
            throw new IllegalArgumentException("Composio user ID is required");
    }

    public static ComposioConfigBuilder builder() {

        return new ComposioConfigBuilder();
    }

    public static class ComposioConfigBuilder {

        private String apiKey;
        private String userId;

        public ComposioConfigBuilder apiKey(String apiKey) { this.apiKey = apiKey; return this; }
        public ComposioConfigBuilder userId(String userId) { this.userId = userId; return this; }

        public ComposioConfig build() {

            return new ComposioConfig(apiKey, userId);
        }
    }
}
