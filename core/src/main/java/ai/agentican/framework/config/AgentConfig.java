package ai.agentican.framework.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Duration;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AgentConfig(
        String id,
        String name,
        String role,
        String llm,
        String runner,
        Integer maxTurns,
        Duration timeout) {

    public static final String RUNNER_SMAC = "smac";
    public static final String RUNNER_REACT = "react";

    public AgentConfig {

        if (id == null || id.isBlank())
            throw new IllegalArgumentException("Agent id is required (name='" + name + "')");

        if (name == null || name.isBlank())
            throw new IllegalArgumentException("Agent name is required");

        if (role == null || role.isBlank())
            throw new IllegalArgumentException("Agent role is required");

        if (llm == null || llm.isBlank())
            llm = LlmConfig.DEFAULT;

        if (runner == null || runner.isBlank())
            runner = RUNNER_SMAC;
    }

    public static AgentConfigBuilder builder() {

        return new AgentConfigBuilder();
    }

    public static class AgentConfigBuilder {

        private String id;
        private String name;
        private String role;
        private String llm;
        private String runner;
        private Integer maxTurns;
        private Duration timeout;

        public AgentConfigBuilder id(String id)              { this.id = id; return this; }
        public AgentConfigBuilder name(String name)          { this.name = name; return this; }
        public AgentConfigBuilder role(String role)          { this.role = role; return this; }
        public AgentConfigBuilder llm(String llm)            { this.llm = llm; return this; }
        public AgentConfigBuilder runner(String runner)      { this.runner = runner; return this; }
        public AgentConfigBuilder maxTurns(Integer maxTurns) { this.maxTurns = maxTurns; return this; }
        public AgentConfigBuilder timeout(Duration timeout)  { this.timeout = timeout; return this; }

        public AgentConfig build() {

            return new AgentConfig(id, name, role, llm, runner, maxTurns, timeout);
        }
    }
}
