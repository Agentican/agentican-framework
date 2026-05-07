package ai.agentican.framework.orchestration.planning;

import ai.agentican.framework.config.AgentConfig;
import ai.agentican.framework.config.WorkflowConfig;
import ai.agentican.framework.config.SkillConfig;
import ai.agentican.framework.orchestration.model.WorkflowDefinition;
import ai.agentican.framework.util.Ids;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Duration;
import java.util.List;

/**
 * The "create new workflow" planner decision shape. The LLM may omit ids on
 * agents, skills, and the workflow itself; {@link #toPlannerResult()} fills
 * any missing id with a generated one before constructing the strict
 * {@link AgentConfig} / {@link SkillConfig} / {@link WorkflowDefinition}
 * records (which all reject null/blank ids).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record WorkflowPlanned(
        String id,
        String name,
        String description,
        List<PlannedAgent> agents,
        List<PlannedSkill> skills,
        List<WorkflowConfig.PlanParamConfig> params,
        List<WorkflowConfig.PlanStepConfig> steps) implements WorkflowPlannerDecision {

    public WorkflowPlannerResult toPlannerResult() {

        var workflowId = (id == null || id.isBlank()) ? Ids.generate() : id;
        var builder = WorkflowDefinition.builder(workflowId, name).description(description);

        if (params != null)
            builder.params(params.stream().map(WorkflowConfig.PlanParamConfig::toWorkflowParam).toList());

        if (steps != null)
            builder.steps(steps.stream().map(WorkflowConfig.PlanStepConfig::toWorkflowStep).toList());

        var resolvedAgents = agents == null ? List.<AgentConfig>of()
                : agents.stream().map(PlannedAgent::toAgentConfig).toList();

        var resolvedSkills = skills == null ? List.<SkillConfig>of()
                : skills.stream().map(PlannedSkill::toSkillConfig).toList();

        return new WorkflowPlannerResult(builder.build(), resolvedAgents, resolvedSkills);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PlannedAgent(
            String id,
            String name,
            String role,
            String llm,
            String runner,
            Integer maxTurns,
            Duration timeout) {

        public AgentConfig toAgentConfig() {

            var resolvedId = (id == null || id.isBlank()) ? Ids.generate() : id;
            return new AgentConfig(resolvedId, name, role, llm, runner, maxTurns, timeout);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PlannedSkill(
            String id,
            String name,
            String instructions) {

        public SkillConfig toSkillConfig() {

            var resolvedId = (id == null || id.isBlank()) ? Ids.generate() : id;
            return new SkillConfig(resolvedId, name, instructions);
        }
    }
}
