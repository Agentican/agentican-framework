package ai.agentican.framework.orchestration.planning;

import ai.agentican.framework.config.AgentConfig;
import ai.agentican.framework.config.SkillConfig;
import ai.agentican.framework.orchestration.model.Plan;

import java.util.List;

public record PlannerResult(
        Plan plan,
        List<AgentConfig> agents,
        List<SkillConfig> skills) {

    public static PlannerResult of(Plan plan, List<AgentConfig> agents, List<SkillConfig> skills) {

        return new PlannerResult(plan, agents, skills);
    }

    public PlannerResult {

        if (plan == null)
            throw new IllegalArgumentException("Plan is required");

        if (agents == null)
            agents = List.of();

        if (skills == null)
            skills = List.of();
    }
}
