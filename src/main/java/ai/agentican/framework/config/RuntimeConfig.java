package ai.agentican.framework.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
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
        List<PlanConfig> plans) {

    private static final Pattern ENV_PATTERN = Pattern.compile("\\$\\{([^}]+)}");
    private static final ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory());

    public RuntimeConfig {

        if (llm == null || llm.isEmpty())
            throw new IllegalArgumentException("At least one LLM configuration is required");

        if (mcp == null) mcp = List.of();
        if (agentRunner == null) agentRunner = new WorkerConfig(0, null);
        if (agents == null) agents = List.of();
        if (plans == null) plans = List.of();
    }

    public static RuntimeConfig load(Path path) throws IOException {

        var raw = Files.readString(path);

        var resolved = resolveEnvVars(raw);

        return YAML_MAPPER.readValue(resolved, RuntimeConfig.class);
    }

    public static RuntimeConfigBuilder builder() {

        return new RuntimeConfigBuilder();
    }

    public static class RuntimeConfigBuilder {

        private final List<LlmConfig> llm = new ArrayList<>();
        private final List<McpConfig> mcp = new ArrayList<>();
        private final List<AgentConfig> agents = new ArrayList<>();
        private final List<PlanConfig> plans = new ArrayList<>();

        private ComposioConfig composio;
        private WorkerConfig worker;

        public RuntimeConfigBuilder llm(LlmConfig llm) { this.llm.add(llm); return this; }
        public RuntimeConfigBuilder composio(ComposioConfig composio) { this.composio = composio; return this; }
        public RuntimeConfigBuilder worker(WorkerConfig worker) { this.worker = worker; return this; }
        public RuntimeConfigBuilder mcp(McpConfig mcp) { this.mcp.add(mcp); return this; }
        public RuntimeConfigBuilder agent(AgentConfig agent) { this.agents.add(agent); return this; }
        public RuntimeConfigBuilder plan(PlanConfig plan) { this.plans.add(plan); return this; }

        public RuntimeConfig build() {

            return new RuntimeConfig(llm, mcp, composio, worker, agents, plans);
        }
    }

    private static String resolveEnvVars(String input) {

        var matcher = ENV_PATTERN.matcher(input);

        var sb = new StringBuilder();

        while (matcher.find()) {

            String envName = matcher.group(1);
            String envValue = System.getenv(envName);

            if (envValue == null)
                throw new IllegalStateException(
                        "Environment variable '" + envName + "' is not set (referenced in config as ${" + envName + "})");

            matcher.appendReplacement(sb, Matcher.quoteReplacement(envValue));
        }

        matcher.appendTail(sb);

        return sb.toString();
    }
}
