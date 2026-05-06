package ai.agentican.framework.orchestration.planning;

import ai.agentican.framework.agent.Agent;
import ai.agentican.framework.registry.AgentRegistry;
import ai.agentican.framework.config.AgentConfig;
import ai.agentican.framework.config.WorkflowConfig;
import ai.agentican.framework.llm.LlmClient;
import ai.agentican.framework.llm.LlmRequest;
import ai.agentican.framework.registry.WorkflowRegistry;
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

public class WorkflowPlannerAgent {

    private static final Logger LOG = LoggerFactory.getLogger(WorkflowPlannerAgent.class);

    private static final Templates TEMPLATES = new Templates();

    private final LlmClient llm;

    private final AgentRegistry agents;
    private final ToolkitRegistry toolkits;
    private final SkillRegistry skills;
    private final WorkflowRegistry workflows;

    private final Function<AgentConfig, Agent> agentFactory;

    private final boolean strict;

    public WorkflowPlannerAgent(LlmClient llm, AgentRegistry agents, ToolkitRegistry toolkits,
                                SkillRegistry skills, WorkflowRegistry workflows,
                                Function<AgentConfig, Agent> agentFactory, boolean strict) {

        if (llm == null) throw new IllegalArgumentException("LLM client is required");
        if (agents == null) throw new IllegalArgumentException("AgentRegistry is required");
        if (toolkits == null) throw new IllegalArgumentException("ToolkitRegistry is required");
        if (skills == null) throw new IllegalArgumentException("SkillRegistry is required");
        if (workflows == null) throw new IllegalArgumentException("WorkflowRegistry is required");
        if (agentFactory == null) throw new IllegalArgumentException("Agent factory is required");

        this.llm = llm;
        this.agents = agents;
        this.toolkits = toolkits;
        this.skills = skills;
        this.workflows = workflows;
        this.agentFactory = agentFactory;
        this.strict = strict;
    }

    public WorkflowPlan plan(String taskDescription) {

        if (taskDescription == null || taskDescription.isBlank())
            throw new IllegalArgumentException("Task is required");

        var workflowDecision = decide(taskDescription);

        if (workflowDecision instanceof WorkflowSelected(String name, Map<String, String> inputs)) {

            var workflow = workflows.byName(name);

            if (workflow != null) {

                LOG.info("Planner reused existing definition '{}' ({})", workflow.name(), workflow.id());

                return new WorkflowPlan(workflow, inputs);
            }

            LOG.warn("Planner referenced definition '{}' which does not exist in the catalog; falling back to create",
                    name);

            workflowDecision = forceCreate(taskDescription);
        }

        if (!(workflowDecision instanceof WorkflowPlanned workflowPlannedDecision))
            throw new IllegalStateException("Planner did not return a create decision on fallback");

        var plannerResult = workflowPlannedDecision.toPlannerResult();

        var planStepsCnt = plannerResult.plan().steps().size();
        var planAgentsCnt = plannerResult.agents().size();
        var planSkillsCnt = plannerResult.skills().size();

        LOG.info(Logs.PLANNER_PLAN_CREATED, planStepsCnt, planAgentsCnt, planSkillsCnt);
        LOG.debug(Logs.PLANNER_PLAN, Json.pretty(plannerResult));

        if (strict) {

            failOnNewDefinitions(plannerResult);
        }
        else {

            plannerResult.skills().forEach(skills::registerIfAbsent);

            plannerResult.agents().stream()
                    .filter(agentConfig -> !agents.hasById(agentConfig.id()))
                    .map(agentFactory)
                    .forEach(agents::register);
        }

        var refinedWfDef = refinePlan(plannerResult.plan());

        var reconciledWfDef = reconcileReferences(refinedWfDef);

        if (strict)
            failOnUnresolvedReferences(reconciledWfDef);

        return new WorkflowPlan(reconciledWfDef, Map.of());
    }

    private WorkflowPlannerDecision decide(String taskDescription) {

        LOG.info(Logs.PLANNER_CREATING);

        var systemPrompt = TEMPLATES.renderPlannerPrompt(agents.asMap().values(), skills.list(),
                toolkits.allToolNames(), workflows.list(), strict);

        var llmRequest = new LlmRequest(systemPrompt, null, taskDescription, List.of(), 0,
                null, null, null, null, List.of());

        var llmResponse = llm.send(llmRequest);

        LOG.info(Logs.PLANNER_RECD_LLM);

        var llmResponseTxt = llmResponse.text();

        return Json.findObject(llmResponseTxt, WorkflowPlannerDecision.class);
    }

    private WorkflowPlanned forceCreate(String taskDescription) {

        var systemPrompt = TEMPLATES.renderPlannerPrompt(
                agents.asMap().values(),
                skills.list(),
                toolkits.allToolNames(),
                List.of(),
                strict);

        var llmRequest = new LlmRequest(systemPrompt, null, taskDescription, List.of(), 0,
                null, null, null, null, List.of());

        var llmResponse = llm.send(llmRequest);

        var llmResponseTxt = llmResponse.text();

        var decision = Json.findObject(llmResponseTxt, WorkflowPlannerDecision.class);

        if (decision instanceof WorkflowPlanned workflowPlannedDecision)
            return workflowPlannedDecision;

        throw new IllegalStateException("Planner returned a non-create decision after fallback retry");
    }

    private WorkflowDefinition refinePlan(WorkflowDefinition initial) {

        var toolNames = collectToolNames(initial.steps());

        if (toolNames.isEmpty()) {

            LOG.info("WorkflowDefinition uses no tools; skipping refinement pass");

            return initial;
        }

        var toolDefs = toolkits.toolDefinitions(List.copyOf(toolNames));

        if (toolDefs.isEmpty()) {

            LOG.warn("WorkflowDefinition references tools but none resolved from the registry; skipping refinement");

            return initial;
        }

        LOG.info("Refining definition: {} steps, {} tool schema(s)", initial.steps().size(), toolDefs.size());

        try {

            var planJson = Json.pretty(initial);

            var userMessage = TEMPLATES.renderRefinePlanMessage(
                    planJson,
                    agents.asMap().values(),
                    skills.list(),
                    ToolView.fromAll(toolDefs));

            var llmRequest = new LlmRequest(TEMPLATES.refinePlanPrompt(), null, userMessage, List.of(), 0,
                    null, null, null, null, List.of());

            var llmResponse = llm.send(llmRequest);

            var llmResponseTxt = llmResponse.text();

            var refinement = Json.findObject(llmResponseTxt, RefinedPlan.class);

            if (refinement == null || refinement.steps == null || refinement.steps.isEmpty()) {

                LOG.warn("Refinement returned empty definition; using initial definition");

                return initial;
            }

            var params = refinement.params != null
                    ? refinement.params.stream().map(pc ->
                            new WorkflowParam(pc.name(), pc.description(), pc.defaultValue(), pc.required())).toList()
                    : initial.params();

            var steps = refinement.steps.stream().map(WorkflowConfig.PlanStepConfig::toWorkflowStep).toList();

            return new WorkflowDefinition(initial.id(), initial.name(), initial.description(), params, steps,
                    initial.outputStep());
        }
        catch (Exception e) {

            LOG.warn("WorkflowDefinition refinement failed: {}; using initial definition", e.getMessage(), e);

            return initial;
        }
    }

    private Set<String> collectToolNames(List<WorkflowStep> steps) {

        var tools = new LinkedHashSet<String>();

        for (var step : steps) {

            switch (step) {

                case WorkflowStepAgent s -> tools.addAll(s.tools());
                case WorkflowStepLoop s -> tools.addAll(collectToolNames(s.body()));
                case WorkflowStepBranch s -> s.paths().forEach(p -> tools.addAll(collectToolNames(p.body())));
                case WorkflowStepCode<?> s -> {  }
            }
        }

        return tools;
    }

    private WorkflowDefinition reconcileReferences(WorkflowDefinition plan) {

        var reconciledSteps = plan.steps().stream().map(this::reconcileStep).toList();

        return new WorkflowDefinition(plan.id(), plan.name(), plan.description(), plan.params(), reconciledSteps,
                plan.outputStep());
    }

    private WorkflowStep reconcileStep(WorkflowStep step) {

        return switch (step) {

            case WorkflowStepAgent s -> reconcileAgentStep(s);

            case WorkflowStepLoop s ->
                    new WorkflowStepLoop(s.name(), s.over(), s.body().stream().map(this::reconcileStep).toList(),
                            s.dependencies(), s.hitl());

            case WorkflowStepBranch s -> new WorkflowStepBranch(s.name(), s.from(),
                    s.paths().stream().map(p -> new WorkflowStepBranch.Path(p.pathName(),
                            p.body().stream().map(this::reconcileStep).toList())).toList(), s.defaultPath(),
                    s.dependencies(), s.hitl());

            case WorkflowStepCode<?> s -> s;
        };
    }

    private WorkflowStepAgent reconcileAgentStep(WorkflowStepAgent step) {

        var resolvedAgentName = resolveAgentRef(step.agentName());
        var resolvedSkills = step.skills().stream().map(this::resolveSkillRef).toList();

        return new WorkflowStepAgent(step.name(), resolvedAgentName, step.instructions(), step.dependencies(),
                step.hitl(), resolvedSkills, step.tools(), step.maxRetries(), step.timeout(), step.conditions(),
                step.conditionMode());
    }

    private String resolveAgentRef(String ref) {

        if (ref == null) return null;

        if (agents.hasByName(ref)) return ref;

        if (agents.hasById(ref)) {

            var agent = agents.byId(ref);

            return agent.name();
        }

        LOG.warn("Step references unknown agent '{}'; leaving as-is", ref);

        return ref;
    }

    private String resolveSkillRef(String ref) {

        if (ref == null) return null;

        if (skills.hasById(ref)) return ref;

        var skill = skills.byName(ref);

        if (skill != null) return skill.id();

        LOG.warn("Step references unknown skill '{}'; leaving as-is", ref);

        return ref;
    }

    private void failOnNewDefinitions(WorkflowPlannerResult result) {

        var newAgents = result.agents() == null ? List.<String>of() : result.agents().stream()
                .filter(a -> !agents.hasById(a.id()))
                .map(a -> a.name() != null ? a.name() : a.id())
                .toList();

        var newSkills = result.skills() == null ? List.<String>of() : result.skills().stream()
                .filter(s -> !skills.hasById(s.id()))
                .map(s -> s.name() != null ? s.name() : s.id())
                .toList();

        if (newAgents.isEmpty() && newSkills.isEmpty()) return;

        throw new StrictPlannerException(
                "Strict mode: planner proposed new agents " + newAgents +
                " and new skills " + newSkills +
                ". Only existing registry entries may be used.");
    }

    private void failOnUnresolvedReferences(WorkflowDefinition plan) {

        var unresolvedAgents = new LinkedHashSet<String>();
        var unresolvedSkills = new LinkedHashSet<String>();

        collectUnresolvedRefs(plan.steps(), unresolvedAgents, unresolvedSkills);

        if (unresolvedAgents.isEmpty() && unresolvedSkills.isEmpty()) return;

        throw new StrictPlannerException(
                "Strict mode: definition references unknown agents " + unresolvedAgents +
                " and unknown skills " + unresolvedSkills +
                ". All references must resolve to a registered entry.");
    }

    private void collectUnresolvedRefs(List<WorkflowStep> steps,
                                       Set<String> unresolvedAgents,
                                       Set<String> unresolvedSkills) {

        for (var step : steps) {

            switch (step) {

                case WorkflowStepAgent s -> {

                    if (s.agentName() != null && !agents.hasByName(s.agentName()))
                        unresolvedAgents.add(s.agentName());

                    for (var skillRef : s.skills())
                        if (skillRef != null && !skills.hasById(skillRef))
                            unresolvedSkills.add(skillRef);
                }
                case WorkflowStepLoop s -> collectUnresolvedRefs(s.body(), unresolvedAgents, unresolvedSkills);
                case WorkflowStepBranch s -> s.paths().forEach(p ->
                        collectUnresolvedRefs(p.body(), unresolvedAgents, unresolvedSkills));
                case WorkflowStepCode<?> s -> {  }
            }
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record RefinedPlan(List<WorkflowConfig.PlanParamConfig> params,
                               List<WorkflowConfig.PlanStepConfig> steps) {}
}
