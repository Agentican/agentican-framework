package ai.agentican.framework.config;

import ai.agentican.framework.util.DotEnv;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@JsonIgnoreProperties(ignoreUnknown = true)
public record RuntimeConfig(
        List<LlmConfig> llm,
        List<McpConfig> mcp,
        ComposioConfig composio,
        WorkerConfig agentRunner,
        List<AgentConfig> agents,
        List<SkillConfig> skills,
        List<WorkflowConfig> workflows,
        boolean strict) {

    private static final Pattern ENV_PATTERN = Pattern.compile("\\$\\{([^}]+)}");
    private static final ObjectMapper YAML_MAPPER =
            new ObjectMapper(new YAMLFactory()).registerModule(new JavaTimeModule());

    public RuntimeConfig {

        if (llm == null) llm = List.of();
        if (mcp == null) mcp = List.of();
        if (agents == null) agents = List.of();
        if (skills == null) skills = List.of();
        if (workflows == null) workflows = List.of();
    }

    public static RuntimeConfig load(Path path) throws IOException {

        return load(Files.readString(path));
    }

    public static RuntimeConfig load(InputStream input) throws IOException {

        return load(new String(input.readAllBytes(), StandardCharsets.UTF_8));
    }

    private static RuntimeConfig load(String raw) throws IOException {

        var resolved = resolveEnvVars(raw);

        return YAML_MAPPER.readValue(resolved, RuntimeConfig.class);
    }

    private static String resolveEnvVars(String input) {

        var matcher = ENV_PATTERN.matcher(input);

        var sb = new StringBuilder();

        while (matcher.find()) {

            String envName = matcher.group(1);
            String envValue = System.getenv(envName);

            if (envValue == null) envValue = DotEnv.get(envName);

            if (envValue == null) {

                var dotEnvInfo = DotEnv.loadedFrom() != null
                        ? "Loaded .env from: " + DotEnv.loadedFrom() + " (but it has no '" + envName + "' entry)."
                        : "No .env file found. Searched:\n  " + String.join(
                                "\n  ", DotEnv.searchedPaths().stream().map(Path::toString).toList());

                throw new IllegalStateException(
                        "Environment variable '" + envName + "' is not set (referenced in config as ${" + envName
                                + "}).\nCWD: " + Path.of(".").toAbsolutePath().normalize()
                                + "\n" + dotEnvInfo);
            }

            matcher.appendReplacement(sb, Matcher.quoteReplacement(envValue));
        }

        matcher.appendTail(sb);

        return sb.toString();
    }
}
