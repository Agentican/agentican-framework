package ai.agentican.framework.orchestration.planning;

import ai.agentican.framework.agent.Agent;
import ai.agentican.framework.agent.AgentRegistry;
import ai.agentican.framework.config.AgentConfig;
import ai.agentican.framework.llm.LlmClient;
import ai.agentican.framework.llm.LlmRequest;
import ai.agentican.framework.orchestration.model.*;
import ai.agentican.framework.tools.ToolkitRegistry;
import ai.agentican.framework.util.Json;
import ai.agentican.framework.util.Logs;
import ai.agentican.framework.util.Parallel;
import ai.agentican.framework.util.Templates;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public class PlannerAgent {

    private static final Logger LOG = LoggerFactory.getLogger(PlannerAgent.class);

    private static final Templates TEMPLATES = new Templates();

    private final LlmClient llm;

    private final AgentRegistry agentRegistry;
    private final ToolkitRegistry toolkitRegistry;

    private final Function<AgentConfig, Agent> agentFactory;

    public PlannerAgent(LlmClient llm, AgentRegistry agentRegistry, ToolkitRegistry toolkitRegistry,
                            Function<AgentConfig, Agent> agentFactory) {

        if (llm == null) throw new IllegalArgumentException("LLM client is required");
        if (agentRegistry == null) throw new IllegalArgumentException("AgentRegistry is required");
        if (toolkitRegistry == null) throw new IllegalArgumentException("ToolkitRegistry is required");
        if (agentFactory == null) throw new IllegalArgumentException("Agent factory is required");

        this.llm = llm;
        this.agentRegistry = agentRegistry;
        this.toolkitRegistry = toolkitRegistry;
        this.agentFactory = agentFactory;
    }

    public Plan plan(String taskDescription) {

        if (taskDescription == null || taskDescription.isBlank())
            throw new IllegalArgumentException("Task is required");

        var plannerResult = createInitialPlan(taskDescription);

        plannerResult.agents().stream()
                .filter(agentConfig -> !agentRegistry.isRegistered(agentConfig.name()))
                .map(agentFactory)
                .forEach(agentRegistry::register);

        var refined = refineAgentSteps(plannerResult.plan());

        return refineLoopSteps(refined);
    }

    private PlannerResult createInitialPlan(String taskDescription) {

        LOG.info(Logs.PLANNER_CREATING);

        var systemPrompt = TEMPLATES.renderPlannerPrompt(agentRegistry.asMap().values(), toolkitRegistry.slugs());

        var llmResponse = llm.send(new LlmRequest(systemPrompt, taskDescription, List.of(), 0));

        LOG.info(Logs.PLANNER_RECD_LLM);

        var plannerOutput = Json.findObject(llmResponse.text(), PlannerOutput.class);
        var plannerResult = plannerOutput.toPlannerResult();

        LOG.info(Logs.PLANNER_PLAN_CREATED, plannerResult.plan().steps().size(), plannerResult.agents().size());
        LOG.debug(Logs.PLANNER_PLAN, Json.pretty(plannerResult));

        return plannerResult;
    }

    private Plan refineAgentSteps(Plan plan) {

        var refinedAgentSteps = Parallel.map(plan.steps(), this::refineStep);

        return new Plan(plan.id(), plan.name(), plan.description(), plan.params(), refinedAgentSteps);
    }

    private PlanStep refineStep(PlanStep step) {

        return switch (step) {
            case PlanStepAgent s -> refineAgentStep(s);
            case PlanStepLoop s -> refineLoopBodySteps(s);
            case PlanStepBranch s -> refineBranchBodySteps(s);
        };
    }

    private PlanStepAgent refineAgentStep(PlanStepAgent step) {

        if (step.toolkits().isEmpty())
            return step;

        var toolDefs = toolkitRegistry.toolDefinitions(step.toolkits());

        if (toolDefs.isEmpty())
            return step;

        var agent = agentRegistry.get(step.agentName());
        var agentRole = agent != null ? agent.role() : step.agentName();

        LOG.info(Logs.PLANNER_REFINE_STEP, "agent", step.name(), step.toolkits().size(), toolDefs.size());

        try {

            var userMessage = TEMPLATES.renderRefineAgentStepMessage(step, agentRole, ToolView.fromAll(toolDefs));

            var llmResponse = llm.send(new LlmRequest(TEMPLATES.refineAgentStepPrompt(), userMessage, List.of(), 0));

            return new PlanStepAgent(step.name(), step.agentName(), llmResponse.text().strip(),
                    step.dependencies(), step.hitl(), step.skills(), step.toolkits());
        }
        catch (Exception e) {

            LOG.warn("Failed to refine agent step '{}', using original: {}", step.name(), e.getMessage());
            return step;
        }
    }

    private PlanStepLoop refineLoopBodySteps(PlanStepLoop step) {

        var refinedBody = Parallel.map(step.body(), this::refineStep);

        return new PlanStepLoop(step.name(), step.over(), refinedBody, step.dependencies(), step.hitl());
    }

    private PlanStepBranch refineBranchBodySteps(PlanStepBranch step) {

        var refinedPaths = Parallel.map(step.paths(), path ->
                new PlanStepBranch.Path(path.pathName(), Parallel.map(path.body(), this::refineStep)));

        return new PlanStepBranch(step.name(), step.from(), refinedPaths, step.defaultPath(),
                step.dependencies(), step.hitl());
    }

    private Plan refineLoopSteps(Plan plan) {

        var nodes = plan.steps();

        var loopSteps = nodes.stream()
                .filter(n -> n instanceof PlanStepLoop)
                .map(n -> (PlanStepLoop) n)
                .toList();

        if (loopSteps.isEmpty())
            return plan;

        var refinements = Parallel.map(loopSteps, loop ->
                Map.entry(loop.name(), refineLoopStep(loop, nodes)));

        var refinementMap = new LinkedHashMap<String, LoopRefinement>();

        for (var entry : refinements)
            if (entry.getValue() != null)
                refinementMap.put(entry.getKey(), entry.getValue());

        if (refinementMap.isEmpty())
            return plan;

        var refinedNodes = new ArrayList<PlanStep>();

        for (var node : nodes) {

            var refinement = node instanceof PlanStepLoop ? refinementMap.get(node.name()) : null;

            if (refinement != null) {

                if (refinement.setupStep() != null)
                    refinedNodes.add(refinement.setupStep());

                refinedNodes.add(refinement.loop());
            }
            else {
                refinedNodes.add(node);
            }
        }

        return new Plan(plan.id(), plan.name(), plan.description(), plan.params(), refinedNodes);
    }

    private LoopRefinement refineLoopStep(PlanStepLoop loop, List<PlanStep> allSteps) {

        var bodyToolkitSlugs = collectToolkitSlugs(loop.body());

        if (bodyToolkitSlugs.isEmpty())
            return null;

        var toolDefs = toolkitRegistry.toolDefinitions(List.copyOf(bodyToolkitSlugs));

        if (toolDefs.isEmpty())
            return null;

        var producerStep = allSteps.stream()
                .filter(s -> s.name().equals(loop.over()))
                .findFirst()
                .orElse(null);

        LOG.info(Logs.PLANNER_REFINE_STEP, "loop", loop.name(), bodyToolkitSlugs.size(), toolDefs.size());

        var producer = producerStep instanceof PlanStepAgent p ? p : null;

        try {

            var userMessage = TEMPLATES.renderRefineControlStepMessage(
                    loop, producer, ToolView.fromAll(toolDefs), agentRegistry.asMap().values());

            var llmResponse = llm.send(new LlmRequest(TEMPLATES.refineControlStepPrompt(), userMessage, List.of(), 0));

            var refinement = Json.findObject(llmResponse.text(), ControlStepRefinement.class);

            return applyRefinement(loop, refinement);
        }
        catch (Exception e) {

            LOG.warn("Failed to refine loop step '{}', using original: {}", loop.name(), e.getMessage());
            return null;
        }
    }

    private Set<String> collectToolkitSlugs(List<PlanStep> steps) {

        var slugs = new LinkedHashSet<String>();

        for (var step : steps) {

            switch (step) {
                case PlanStepAgent s -> slugs.addAll(s.toolkits());
                case PlanStepLoop s -> slugs.addAll(collectToolkitSlugs(s.body()));
                case PlanStepBranch s -> s.paths().forEach(p -> slugs.addAll(collectToolkitSlugs(p.body())));
            }
        }

        return slugs;
    }

    private LoopRefinement applyRefinement(PlanStepLoop loop, ControlStepRefinement refinement) {

        var refinedBody = loop.body();

        if (refinement.bodySteps != null && !refinement.bodySteps.isEmpty()) {

            var refinedMap = refinement.bodySteps.stream()
                    .collect(Collectors.toMap(bs -> bs.name, bs -> bs.instructions, (a, _) -> a));

            refinedBody = loop.body().stream()
                    .map(step -> {

                        if (step instanceof PlanStepAgent s && refinedMap.containsKey(s.name()))
                            return (PlanStep) new PlanStepAgent(
                                    s.name(), s.agentName(), refinedMap.get(s.name()),
                                    s.dependencies(), s.hitl(), s.skills(), s.toolkits());

                        return step;
                    })
                    .toList();
        }

        var loopOver = refinement.loopOver != null ? refinement.loopOver : loop.over();

        var refinedLoop = new PlanStepLoop(loop.name(), loopOver, refinedBody,
                loop.dependencies(), loop.hitl());

        PlanStepAgent setupStep = null;

        if (refinement.setupStep != null) {

            if (!agentRegistry.isRegistered(refinement.setupStep.agent)) {

                LOG.warn("Pass 3: setup step references unknown agent '{}', skipping setup", refinement.setupStep.agent);
            }
            else {

                setupStep = new PlanStepAgent(
                        refinement.setupStep.name,
                        refinement.setupStep.agent,
                        refinement.setupStep.instructions,
                        refinement.setupStep.dependencies != null ? refinement.setupStep.dependencies : List.of(loop.over()),
                        false,
                        List.of(),
                        refinement.setupStep.toolkits != null ? refinement.setupStep.toolkits : List.of());
            }
        }

        return new LoopRefinement(refinedLoop, setupStep);
    }

    private record LoopRefinement(PlanStepLoop loop, PlanStepAgent setupStep) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    private record ControlStepRefinement(
            SetupStepDef setupStep,
            List<BodyStepRef> bodySteps,
            String loopOver) {

        @JsonIgnoreProperties(ignoreUnknown = true)
        @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
        record SetupStepDef(String name, String agent, String instructions,
                            List<String> toolkits, List<String> dependencies) {}

        @JsonIgnoreProperties(ignoreUnknown = true)
        record BodyStepRef(String name, String instructions) {}
    }
}
