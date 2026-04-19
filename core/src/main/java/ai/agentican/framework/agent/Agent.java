package ai.agentican.framework.agent;

import ai.agentican.framework.config.AgentConfig;
import ai.agentican.framework.state.RunLog;
import ai.agentican.framework.tools.ToolResult;
import ai.agentican.framework.tools.Toolkit;

import java.time.Duration;
import java.util.List;
import java.util.Map;

public record Agent(
        AgentConfig config,
        AgentRunner runner) {

    public Agent {

        if (config == null)
            throw new IllegalArgumentException("AgentConfig is required");

        if (runner == null)
            throw new IllegalArgumentException("AgentRunner is required");
    }

    public AgentResult run(String task, List<String> activeSkills, Map<String, Toolkit> toolkits, String taskId,
                           String stepId, String stepName, Duration timeoutOverride) {

        return runner.run(this, task, activeSkills, toolkits, taskId, stepId, stepName, timeoutOverride);
    }

    public AgentResult resume(String task, List<String> activeSkills, RunLog savedRun,
                              List<ToolResult> hitlToolResults, Map<String, Toolkit> toolkits, String taskId,
                              String stepId, String stepName, Duration timeoutOverride) {

        return runner.resume(this, task, activeSkills, savedRun, hitlToolResults, toolkits, taskId, stepId,
                stepName, timeoutOverride);
    }

    public String id()   { return config.id(); }
    public String name() { return config.name(); }
    public String role() { return config.role(); }

    public static Builder builder() {

        return new Builder();
    }

    public static class Builder {

        private AgentConfig config;
        private AgentRunner runner;

        Builder() {}

        public Builder config(AgentConfig config) { this.config = config; return this; }
        public Builder runner(AgentRunner runner) { this.runner = runner; return this; }

        public Agent build() {

            return new Agent(config, runner);
        }
    }
}
