package ai.agentican.framework.agent;

import ai.agentican.framework.config.AgentConfig;
import ai.agentican.framework.llm.StructuredOutput;
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

    public AgentResult run(String task, String taskId, String stepId, String stepName, Duration timeout,
                           List<String> skills, Map<String, Toolkit> toolkits) {

        return run(task, taskId, stepId, stepName, timeout, skills, toolkits, null);
    }

    public AgentResult run(String task, String taskId, String stepId, String stepName, Duration timeout,
                           List<String> skills, Map<String, Toolkit> toolkits, StructuredOutput outputSchema) {

        return runner.run(this, task, taskId, stepId, stepName, timeout, skills, toolkits, outputSchema);
    }

    public AgentResult resume(String task, String taskId, String stepId, String stepName, Duration timeout,
                              List<String> skills, Map<String, Toolkit> toolkits, RunLog run,
                              List<ToolResult> hitlToolResults) {

        return resume(task, taskId, stepId, stepName, timeout, skills, toolkits,
                run, hitlToolResults, null);
    }

    public AgentResult resume(String task, String taskId, String stepId, String stepName, Duration timeout,
                              List<String> skills, Map<String, Toolkit> toolkits, RunLog run,
                              List<ToolResult> hitlToolResults, StructuredOutput outputSchema) {

        return runner.resume(this, task, taskId, stepId, stepName, timeout, skills, toolkits, outputSchema,
                run, hitlToolResults);
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
