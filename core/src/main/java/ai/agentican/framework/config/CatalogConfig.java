package ai.agentican.framework.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;

/**
 * Catalog data — agents, skills, and workflow definitions that seed the
 * framework's registries at boot.
 *
 * <p>Loaded by {@code Agentican.builder().registry().yaml()}. Equivalent
 * programmatic setters live on {@code Registry.api()}.
 *
 * <p>For framework-wiring config (LLMs, MCP, etc.) see {@link EngineConfig}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CatalogConfig(
        List<AgentConfig> agents,
        List<SkillConfig> skills,
        List<WorkflowConfig> workflows) {

    public CatalogConfig {

        if (agents == null) agents = List.of();
        if (skills == null) skills = List.of();
        if (workflows == null) workflows = List.of();
    }

    public static CatalogConfig load(Path path) throws IOException {

        return ConfigYaml.load(path, CatalogConfig.class);
    }

    public static CatalogConfig load(InputStream input) throws IOException {

        return ConfigYaml.load(input, CatalogConfig.class);
    }
}
