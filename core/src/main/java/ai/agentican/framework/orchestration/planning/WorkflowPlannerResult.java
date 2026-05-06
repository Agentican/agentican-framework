package ai.agentican.framework.orchestration.planning;

import ai.agentican.framework.config.AgentConfig;
import ai.agentican.framework.config.SkillConfig;
import ai.agentican.framework.orchestration.model.WorkflowDefinition;

import java.util.List;

public record WorkflowPlannerResult(
        WorkflowDefinition plan,
        List<AgentConfig> agents,
        List<SkillConfig> skills) {

    public WorkflowPlannerResult {

        if (plan == null)
            throw new IllegalArgumentException("WorkflowDefinition is required");

        if (agents == null)
            agents = List.of();

        if (skills == null)
            skills = List.of();
    }
}
