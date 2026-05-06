package ai.agentican.framework.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;

/**
 * Framework-wiring config — LLM clients, MCP servers, Composio, the agent runner,
 * and the strict flag. Everything that boots the framework but isn't catalog data.
 *
 * <p>Loaded by {@code Agentican.builder().configuration().yaml()}. Fields that
 * can also be set programmatically live on {@code Configuration.api()}.
 *
 * <p>For catalog data (agents, skills, workflows) see {@link CatalogConfig}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record EngineConfig(
        List<LlmConfig> llm,
        List<McpConfig> mcp,
        ComposioConfig composio,
        WorkerConfig agentRunner,
        boolean strict) {

    public EngineConfig {

        if (llm == null) llm = List.of();
        if (mcp == null) mcp = List.of();
    }

    public static EngineConfig load(Path path) throws IOException {

        return ConfigYaml.load(path, EngineConfig.class);
    }

    public static EngineConfig load(InputStream input) throws IOException {

        return ConfigYaml.load(input, EngineConfig.class);
    }
}
