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
     * Catalog (agents/skills/plans) load source. Engine config (LLM, MCP, composio,
     * strict, agentRunner) is independent of this and always read from these properties,
     * with YAML as a fallback when present.
     *
     * <ul>
     *   <li>{@code yaml} — load agents/skills/plans from {@link #config()} (default {@code agentican.yaml})</li>
     *   <li>{@code database} — load from the JPA-backed registries; useful with {@code quarkus-store-jpa}</li>
     * </ul>
     */
    enum Catalog { yaml, database }

    @WithDefault("yaml")
    Catalog catalog();

    @WithDefault("agentican.yaml")
    String config();

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
