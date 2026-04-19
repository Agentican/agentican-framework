package ai.agentican.framework.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.LinkedHashMap;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record McpConfig(
        String slug,
        String name,
        String url,
        Map<String, String> queryParams,
        Map<String, String> headers) {

    public McpConfig {

        if (slug == null || slug.isBlank())
            throw new IllegalArgumentException("MCP slug is required");

        if (name == null || name.isBlank())
            throw new IllegalArgumentException("MCP name is required");

        if (url == null || url.isBlank())
            throw new IllegalArgumentException("MCP URL is required");

        if (queryParams == null) queryParams = Map.of();
        if (headers == null) headers = Map.of();
    }

    public static McpConfigBuilder builder() {

        return new McpConfigBuilder();
    }

    public static class McpConfigBuilder {

        private String slug;
        private String name;
        private String url;

        private final Map<String, String> queryParams = new LinkedHashMap<>();
        private final Map<String, String> headers = new LinkedHashMap<>();

        public McpConfigBuilder slug(String slug) { this.slug = slug; return this; }
        public McpConfigBuilder name(String name) { this.name = name; return this; }
        public McpConfigBuilder url(String url) { this.url = url; return this; }
        public McpConfigBuilder queryParam(String key, String value) { this.queryParams.put(key, value); return this; }
        public McpConfigBuilder header(String key, String value) { this.headers.put(key, value); return this; }

        public McpConfig build() {

            return new McpConfig(slug, name, url, queryParams, headers);
        }
    }
}
