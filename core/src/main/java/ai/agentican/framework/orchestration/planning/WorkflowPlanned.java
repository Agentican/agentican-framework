package ai.agentican.framework.orchestration.planning;

import ai.agentican.framework.config.AgentConfig;
import ai.agentican.framework.config.WorkflowConfig;
import ai.agentican.framework.config.SkillConfig;
import ai.agentican.framework.orchestration.model.WorkflowDefinition;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record WorkflowPlanned(
        String name,
        String description,
        List<AgentConfig> agents,
        List<SkillConfig> skills,
        List<WorkflowConfig.PlanParamConfig> params,
        List<WorkflowConfig.PlanStepConfig> steps) implements WorkflowPlannerDecision {

    public WorkflowPlannerResult toPlannerResult() {

        var builder = WorkflowDefinition.builder(name).description(description);

        if (params != null)
            params.forEach(p -> builder.param(p.toWorkflowParam()));

        if (steps != null)
            steps.forEach(s -> builder.step(s.toWorkflowStep()));

        return new WorkflowPlannerResult(builder.build(), agents, skills);
    }
}
