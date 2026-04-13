package ai.agentican.framework.agent;

import ai.agentican.framework.config.SkillConfig;
import ai.agentican.framework.tools.Toolkit;

import java.util.List;
import java.util.Map;

public record Agent(
        String name,
        String role,
        List<SkillConfig> skills,
        AgentRunner runner) {

    public static Agent of(String name, String role, List<SkillConfig> skills, AgentRunner runner) {

        return new Agent(name, role, skills, runner);
    }

    public Agent {

        if (name == null || name.isBlank())
            throw new IllegalArgumentException("Agent name is required");

        if (role == null || role.isBlank())
            throw new IllegalArgumentException("Agent role is required");

        if (runner == null)
            throw new IllegalArgumentException("AgentRunner is required");

        if (skills == null)
            skills = List.of();
    }

    public AgentResult run(String task, List<String> activeSkills, Map<String, Toolkit> toolkits, String taskId,
                           String stepId, String stepName) {

        return runner.run(this, task, activeSkills, toolkits, taskId, stepId, stepName);
    }
}
