package ai.agentican.framework.agent;

import ai.agentican.framework.config.AgentConfig;
import ai.agentican.framework.tools.Toolkit;
import ai.agentican.framework.util.Ids;

import java.util.List;
import java.util.Map;

public record Agent(
        String id,
        String name,
        String role,
        AgentRunner runner,
        AgentConfig config) {

    public static Agent of(String name, String role, AgentRunner runner) {

        return new Agent(null, name, role, runner, null);
    }

    public static Agent of(String id, String name, String role, AgentRunner runner) {

        return new Agent(id, name, role, runner, null);
    }

    public static Agent of(AgentConfig config, AgentRunner runner) {

        if (config == null)
            throw new IllegalArgumentException("AgentConfig is required");

        return new Agent(config.id(), config.name(), config.role(), runner, config);
    }

    public Agent {

        if (name == null || name.isBlank())
            throw new IllegalArgumentException("Agent name is required");

        if (role == null || role.isBlank())
            throw new IllegalArgumentException("Agent role is required");

        if (runner == null)
            throw new IllegalArgumentException("AgentRunner is required");

        if (id == null || id.isBlank())
            id = Ids.generate();
    }

    public AgentResult run(String task, List<String> activeSkills, Map<String, Toolkit> toolkits, String taskId,
                           String stepId, String stepName) {

        return runner.run(this, task, activeSkills, toolkits, taskId, stepId, stepName);
    }
}
