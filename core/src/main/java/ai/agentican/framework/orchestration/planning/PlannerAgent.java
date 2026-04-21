package ai.agentican.framework.orchestration.planning;

import ai.agentican.framework.agent.Agent;
import ai.agentican.framework.registry.AgentRegistry;
import ai.agentican.framework.config.AgentConfig;
import ai.agentican.framework.config.PlanConfig;
import ai.agentican.framework.llm.LlmClient;
import ai.agentican.framework.llm.LlmRequest;
import ai.agentican.framework.registry.PlanRegistry;
import ai.agentican.framework.orchestration.model.*;
import ai.agentican.framework.registry.SkillRegistry;
import ai.agentican.framework.registry.ToolkitRegistry;
import ai.agentican.framework.util.Json;
import ai.agentican.framework.util.Logs;
import ai.agentican.framework.util.Templates;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.Function;

public class PlannerAgent {

    private static final Logger LOG = LoggerFactory.getLogger(PlannerAgent.class);

    private static final Templates TEMPLATES = new Templates();

    private final LlmClient llm;

    private final AgentRegistry agentRegistry;
    private final ToolkitRegistry toolkitRegistry;
    private final SkillRegistry skillRegistry;
    private final PlanRegistry planRegistry;

    private final Function<AgentConfig, Agent> agentFactory;

    public PlannerAgent(LlmClient llm, AgentRegistry agentRegistry, ToolkitRegistry toolkitRegistry,
                            SkillRegistry skillRegistry, PlanRegistry planRegistry,
                            Function<AgentConfig, Agent> agentFactory) {

        if (llm == null) throw new IllegalArgumentException("LLM client is required");
        if (agentRegistry == null) throw new IllegalArgumentException("AgentRegistry is required");
        if (toolkitRegistry == null) throw new IllegalArgumentException("ToolkitRegistry is required");
        if (skillRegistry == null) throw new IllegalArgumentException("SkillRegistry is required");
        if (planRegistry == null) throw new IllegalArgumentException("PlanRegistry is required");
        if (agentFactory == null) throw new IllegalArgumentException("Agent factory is required");

        this.llm = llm;
        this.agentRegistry = agentRegistry;
        this.toolkitRegistry = toolkitRegistry;
        this.skillRegistry = skillRegistry;
        this.planRegistry = planRegistry;
        this.agentFactory = agentFactory;
    }

    public PlanningResult plan(String taskDescription) {

        if (taskDescription == null || taskDescription.isBlank())
            throw new IllegalArgumentException("Task is required");

        var decision = decide(taskDescription);

        if (decision instanceof ReuseExisting reuse) {

            var reused = planRegistry.getById(reuse.planRef());

            if (reused != null) {
                LOG.info("Planner reused existing plan '{}' ({})", reused.name(), reused.id());
                return new PlanningResult(reused, reuse.inputs());
            }

            LOG.warn("Planner referenced plan '{}' which does not exist in the catalog; falling back to create",
                    reuse.planRef());

            decision = forceCreate(taskDescription);
        }

        if (!(decision instanceof PlannerOutput output))
            throw new IllegalStateException("Planner did not return a create decision on fallback");

        var plannerResult = output.toPlannerResult();

        LOG.info(Logs.PLANNER_PLAN_CREATED, plannerResult.plan().steps().size(),
                plannerResult.agents().size(), plannerResult.skills().size());
        LOG.debug(Logs.PLANNER_PLAN, Json.pretty(plannerResult));

        plannerResult.skills().forEach(skillRegistry::registerIfAbsent);

        plannerResult.agents().stream()
                .filter(agentConfig -> !agentRegistry.isRegistered(agentConfig.id()))
                .map(agentFactory)
                .forEach(agentRegistry::register);

        var refined = refinePlan(plannerResult.plan());

        return new PlanningResult(reconcileReferences(refined), Map.of());
    }

    private PlannerDecision decide(String taskDescription) {

        LOG.info(Logs.PLANNER_CREATING);

        var systemPrompt = TEMPLATES.renderPlannerPrompt(
                agentRegistry.asMap().values(),
                skillRegistry.getAll(),
                toolkitRegistry.allToolNames(),
                planRegistry.getAll());

        var llmResponse = llm.send(new LlmRequest(systemPrompt, null, taskDescription, List.of(), 0, null, null, null));

        LOG.info(Logs.PLANNER_RECD_LLM);

        return Json.findObject(llmResponse.text(), PlannerDecision.class);
    }

    private PlannerOutput forceCreate(String taskDescription) {

        var systemPrompt = TEMPLATES.renderPlannerPrompt(
                agentRegistry.asMap().values(),
                skillRegistry.getAll(),
                toolkitRegistry.allToolNames(),
                List.of());

        var llmResponse = llm.send(new LlmRequest(systemPrompt, null, taskDescription, List.of(), 0, null, null, null));

        var decision = Json.findObject(llmResponse.text(), PlannerDecision.class);

        if (decision instanceof PlannerOutput output)
            return output;

        throw new IllegalStateException("Planner returned a non-create decision after fallback retry");
    }

    private Plan refinePlan(Plan initial) {

        var toolNames = collectToolNames(initial.steps());

        if (toolNames.isEmpty()) {
            LOG.info("Plan uses no tools; skipping refinement pass");
            return initial;
        }

        var toolDefs = toolkitRegistry.toolDefinitions(List.copyOf(toolNames));

        if (toolDefs.isEmpty()) {
            LOG.warn("Plan references tools but none resolved from the registry; skipping refinement");
            return initial;
        }

        LOG.info("Refining plan: {} steps, {} tool schema(s)", initial.steps().size(), toolDefs.size());

        try {

            var planJson = Json.pretty(initial);

            var userMessage = TEMPLATES.renderRefinePlanMessage(
                    planJson,
                    agentRegistry.asMap().values(),
                    skillRegistry.getAll(),
                    ToolView.fromAll(toolDefs));

            var llmResponse = llm.send(new LlmRequest(TEMPLATES.refinePlanPrompt(), null, userMessage, List.of(), 0, null, null, null));

            var refinement = Json.findObject(llmResponse.text(), RefinedPlan.class);

            if (refinement == null || refinement.steps == null || refinement.steps.isEmpty()) {
                LOG.warn("Refinement returned empty plan; using initial plan");
                return initial;
            }

            var params = refinement.params != null
                    ? refinement.params.stream().map(pc ->
                            new PlanParam(pc.name(), pc.description(), pc.defaultValue(), pc.required())).toList()
                    : initial.params();

            var steps = refinement.steps.stream().map(PlanConfig.PlanStepConfig::toPlanStep).toList();

            return new Plan(initial.id(), initial.name(), initial.description(),
                    params, steps, initial.externalId(), initial.outputStep());
        }
        catch (Exception e) {

            LOG.warn("Plan refinement failed: {}; using initial plan", e.getMessage(), e);
            return initial;
        }
    }

    private Set<String> collectToolNames(List<PlanStep> steps) {

        var tools = new LinkedHashSet<String>();

        for (var step : steps) {
            switch (step) {
                case PlanStepAgent s -> tools.addAll(s.tools());
                case PlanStepLoop s -> tools.addAll(collectToolNames(s.body()));
                case PlanStepBranch s -> s.paths().forEach(p -> tools.addAll(collectToolNames(p.body())));
                case PlanStepCode<?> s -> { /* code steps don't reference toolkit tools */ }
            }
        }

        return tools;
    }

    private Plan reconcileReferences(Plan plan) {

        var reconciledSteps = plan.steps().stream().map(this::reconcileStep).toList();

        return new Plan(plan.id(), plan.name(), plan.description(),
                plan.params(), reconciledSteps, plan.externalId(), plan.outputStep());
    }

    private PlanStep reconcileStep(PlanStep step) {

        return switch (step) {

            case PlanStepAgent s -> reconcileAgentStep(s);

            case PlanStepLoop s -> new PlanStepLoop(s.name(), s.over(),
                    s.body().stream().map(this::reconcileStep).toList(),
                    s.dependencies(), s.hitl());

            case PlanStepBranch s -> new PlanStepBranch(s.name(), s.from(),
                    s.paths().stream().map(p -> new PlanStepBranch.Path(p.pathName(),
                            p.body().stream().map(this::reconcileStep).toList())).toList(),
                    s.defaultPath(), s.dependencies(), s.hitl());

            case PlanStepCode<?> s -> s;  // code steps reference a registered slug; nothing to reconcile
        };
    }

    private PlanStepAgent reconcileAgentStep(PlanStepAgent step) {

        var resolvedAgentId = resolveAgentRef(step.agentId());
        var resolvedSkills = step.skills().stream().map(this::resolveSkillRef).toList();

        return new PlanStepAgent(step.name(), resolvedAgentId, step.instructions(),
                step.dependencies(), step.hitl(), resolvedSkills, step.tools(),
                step.maxRetries(), step.timeout(), step.conditions(), step.conditionMode());
    }

    private String resolveAgentRef(String ref) {

        if (ref == null) return null;
        if (agentRegistry.isRegistered(ref)) return ref;

        var agent = agentRegistry.getByName(ref);

        if (agent != null) return agent.id();

        LOG.warn("Step references unknown agent '{}'; leaving as-is", ref);
        return ref;
    }

    private String resolveSkillRef(String ref) {

        if (ref == null) return null;
        if (skillRegistry.isRegistered(ref)) return ref;

        var skill = skillRegistry.getByName(ref);

        if (skill != null) return skill.id();

        LOG.warn("Step references unknown skill '{}'; leaving as-is", ref);
        return ref;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record RefinedPlan(
            List<PlanConfig.PlanParamConfig> params,
            List<PlanConfig.PlanStepConfig> steps) {}
}
