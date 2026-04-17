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

    List<LlmConfig> llm();

    Optional<AgentRunnerConfig> agentRunner();

    Optional<ComposioConfig> composio();

    List<McpConfig> mcp();

    List<AgentConfig> agents();

    List<SkillConfig> skills();

    @WithDefault("true")
    boolean resumeOnStart();

    @WithDefault("10")
    int resumeMaxConcurrent();

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

    interface AgentConfig {

        Optional<String> id();

        Optional<String> externalId();

        @NotBlank
        String name();

        @NotBlank
        String role();

        Optional<String> llm();
    }

    interface SkillConfig {

        Optional<String> id();

        Optional<String> externalId();

        @NotBlank
        String name();

        @NotBlank
        String instructions();
    }
}
