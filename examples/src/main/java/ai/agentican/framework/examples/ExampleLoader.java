/**
 * Shared utility for loading agent and skill definitions from YAML files.
 *
 * Requires jackson-dataformat-yaml on the classpath:
 *   com.fasterxml.jackson.dataformat:jackson-dataformat-yaml
 */
package ai.agentican.framework.examples;

import ai.agentican.framework.config.AgentConfig;
import ai.agentican.framework.config.SkillConfig;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

public class ExampleLoader {

    private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory());

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Defs(List<AgentConfig> agents, List<SkillConfig> skills) {

        public Defs {
            if (agents == null) agents = List.of();
            if (skills == null) skills = List.of();
        }
    }

    public static Defs load(String resourceName) {

        try (InputStream is = ExampleLoader.class.getClassLoader().getResourceAsStream(resourceName)) {
            if (is == null) throw new IOException("Resource not found: " + resourceName);
            return YAML.readValue(is, Defs.class);
        }
        catch (IOException e) {
            throw new RuntimeException("Failed to load " + resourceName, e);
        }
    }
}
