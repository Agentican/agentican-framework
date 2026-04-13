package ai.agentican.framework.orchestration.planning;

import ai.agentican.framework.config.AgentConfig;
import ai.agentican.framework.orchestration.model.Plan;

import java.util.List;

public record PlannerResult(
        Plan plan,
        List<AgentConfig> agents) {

    public static PlannerResult of(Plan plan, List<AgentConfig> agents) {

        return new PlannerResult(plan, agents);
    }

    public PlannerResult {

        if (plan == null)
            throw new IllegalArgumentException("Plan is required");

        if (agents == null)
            agents = List.of();
    }
}
