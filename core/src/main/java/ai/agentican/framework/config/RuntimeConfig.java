package ai.agentican.framework.config;

import ai.agentican.framework.util.DotEnv;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.IOException;
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
        List<PlanConfig> plans) {

    private static final Pattern ENV_PATTERN = Pattern.compile("\\$\\{([^}]+)}");
    private static final ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory());

    public RuntimeConfig {

        if (llm == null) llm = List.of();
        if (mcp == null) mcp = List.of();
        if (agentRunner == null) agentRunner = new WorkerConfig(0, null);
        if (agents == null) agents = List.of();
        if (skills == null) skills = List.of();
        if (plans == null) plans = List.of();
    }

    public static RuntimeConfig load(Path path) throws IOException {

        var raw = Files.readString(path);

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
