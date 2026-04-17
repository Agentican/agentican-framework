package ai.agentican.framework.orchestration.planning;

import ai.agentican.framework.config.AgentConfig;
import ai.agentican.framework.config.PlanConfig;
import ai.agentican.framework.config.SkillConfig;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PlannerOutput(
        String name,
        String description,
        List<AgentConfig> agents,
        List<SkillConfig> skills,
        List<PlanConfig.PlanParamConfig> paramConfigs,
        List<PlanConfig.PlanStepConfig> stepConfigs) implements PlannerDecision {

    public PlannerResult toPlannerResult() {

        var planConfig = new PlanConfig(name, description, paramConfigs, stepConfigs);

        return new PlannerResult(planConfig.toPlan(), agents, skills);
    }
}
