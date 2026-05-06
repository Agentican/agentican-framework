package ai.agentican.quarkus;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@ConfigMapping(prefix = "agentican")
public interface AgenticanConfig {

    /**
     * Catalog (agents/skills/workflows) load source.
     *
     * <ul>
     *   <li>{@code yaml} — load from the classpath resource at {@link #catalogConfig()}
     *       (default {@code agentican.catalog.yaml})</li>
     *   <li>{@code database} — load from the JPA-backed registries; useful with {@code quarkus-store-jpa}</li>
     * </ul>
     */
    enum CatalogSource { yaml, database }

    @WithDefault("yaml")
    CatalogSource catalogSource();

    /**
     * Classpath resource for engine config (LLM, MCP, composio, agentRunner, strict).
     * Optional; if missing, engine config is built from {@code agentican.*} properties only.
     * If present, properties take precedence over YAML for any field set in both places.
     */
    @WithDefault("agentican.engine.yaml")
    String engineConfig();

    /**
     * Classpath resource for catalog config (agents, skills, workflows).
     * Consulted only when {@link #catalogSource()} is {@code yaml}.
     */
    @WithDefault("agentican.catalog.yaml")
    String catalogConfig();

    List<LlmConfig> llm();

    Optional<AgentRunnerConfig> agentRunner();

    Optional<ComposioConfig> composio();

    List<McpConfig> mcp();

    @WithDefault("true")
    boolean resumeOnStart();

    @WithDefault("10")
    int resumeMaxConcurrent();

    @WithDefault("false")
    boolean strict();

    interface LlmConfig {

        @WithDefault("default")
        @NotBlank
        String name();

        @WithDefault("anthropic")
        @NotBlank
        String provider();

        Optional<String> model();

        @NotBlank
        String apiKey();

        @WithDefault("16384")
        @Min(1)
        int maxTokens();
    }

    interface AgentRunnerConfig {

        @WithDefault("10")
        @Min(1)
        int maxTurns();

        @WithDefault("PT30M")
        Duration timeout();

        Optional<Duration> taskTimeout();
    }

    interface ComposioConfig {

        @NotBlank
        String apiKey();

        @NotBlank
        String userId();
    }

    interface McpConfig {

        @NotBlank
        String slug();

        @NotBlank
        String name();

        @NotBlank
        String url();

        Map<String, String> queryParams();

        Map<String, String> headers();
    }
}
