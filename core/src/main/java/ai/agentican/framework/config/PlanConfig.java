package ai.agentican.framework.config;

import ai.agentican.framework.orchestration.code.CodeStepRegistry;
import ai.agentican.framework.orchestration.model.PlanStepAgent;
import ai.agentican.framework.orchestration.model.PlanStepBranch;
import ai.agentican.framework.orchestration.model.PlanStepCode;
import ai.agentican.framework.orchestration.model.PlanStepLoop;
import ai.agentican.framework.orchestration.model.Plan;
import ai.agentican.framework.orchestration.model.PlanStep;
import ai.agentican.framework.orchestration.model.PlanParam;
import ai.agentican.framework.util.Json;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

@JsonIgnoreProperties(ignoreUnknown = true)
public record  PlanConfig(
        String name,
        String description,
        List<PlanParamConfig> params,
        List<PlanStepConfig> steps,
        String externalId,
        String outputStep) {

    public PlanConfig {

        if (name == null || name.isBlank())
            throw new IllegalArgumentException("Task name is required");

        if (steps == null)
            steps = List.of();

        if (params == null)
            params = List.of();

        if (externalId != null && externalId.isBlank())
            externalId = null;

        if (outputStep != null && outputStep.isBlank())
            outputStep = null;
    }

    public static PlanConfigBuilder builder() {

        return new PlanConfigBuilder();
    }

    public Plan toPlan() {

        return toPlan(null);
    }

    public Plan toPlan(CodeStepRegistry codeStepRegistry) {

        var builder = Plan.builder(name)
                .description(description)
                .externalId(externalId)
                .outputStep(outputStep);

        params.forEach(tpc ->
                builder.param(new PlanParam(tpc.name(), tpc.description(), tpc.defaultValue(), tpc.required())));

        steps.forEach(s -> builder.step(s.toPlanStep(codeStepRegistry)));

        return builder.build();
    }

    public static class PlanConfigBuilder {

        private String name;
        private String description;
        private String externalId;

        private final List<PlanParamConfig> params = new ArrayList<>();
        private final List<PlanStepConfig> steps = new ArrayList<>();

        private String outputStep;

        public PlanConfigBuilder name(String name) { this.name = name; return this; }
        public PlanConfigBuilder description(String description) { this.description = description; return this; }
        public PlanConfigBuilder externalId(String externalId) { this.externalId = externalId; return this; }
        public PlanConfigBuilder outputStep(String stepName) { this.outputStep = stepName; return this; }

        public PlanConfigBuilder param(PlanParamConfig param) { this.params.add(param); return this; }
        public PlanConfigBuilder param(String name, String description, String defaultValue, boolean required) {
            this.params.add(new PlanParamConfig(name, description, defaultValue, required));
            return this;
        }

        public PlanConfigBuilder step(PlanStepConfig step) { this.steps.add(step); return this; }
        public PlanConfigBuilder steps(List<PlanStepConfig> steps) { this.steps.addAll(steps); return this; }

        public PlanConfigBuilder step(String name, Consumer<StepBuilder> config) {
            var b = new StepBuilder(name);
            config.accept(b);
            this.steps.add(b.build());
            return this;
        }

        public PlanConfigBuilder loop(String name, Consumer<LoopBuilder> config) {
            var b = new LoopBuilder(name);
            config.accept(b);
            this.steps.add(b.build());
            return this;
        }

        public PlanConfigBuilder branch(String name, Consumer<BranchBuilder> config) {
            var b = new BranchBuilder(name);
            config.accept(b);
            this.steps.add(b.build());
            return this;
        }

        public PlanConfig build() {

            return new PlanConfig(name, description, params, steps, externalId, outputStep);
        }
    }

    public static class StepBuilder {

        private final String name;

        private String agent;
        private String instructions;
        private List<String> skills = List.of();
        private List<String> tools = List.of();

        private String codeSlug;
        private Object input;

        private List<String> dependencies = List.of();
        private boolean hitl;

        StepBuilder(String name) { this.name = name; }

        public AgentStepBuilder agent(String agent) {

            if (codeSlug != null)
                throw new IllegalStateException(
                        "Step '" + name + "' already declared code('" + codeSlug + "'); cannot also call agent()");

            this.agent = agent;

            return new AgentStepBuilder(this);
        }

        public CodeStepBuilder code(String slug) {

            if (agent != null)
                throw new IllegalStateException(
                        "Step '" + name + "' already declared agent('" + agent + "'); cannot also call code()");

            this.codeSlug = slug;

            return new CodeStepBuilder(this);
        }

        PlanStepConfig build() {

            if (agent != null)
                return new PlanStepConfig(name, "agent", agent, instructions, dependencies, hitl, skills, tools,
                        null, null, null, null, null, null, null);

            if (codeSlug != null)
                return new PlanStepConfig(name, "code", null, null, dependencies, false, null, null,
                        null, null, null, null, null, codeSlug, input);

            throw new IllegalStateException(
                    "Step '" + name + "' must declare either .agent(\"...\") or .code(\"slug\")");
        }
    }

    public static class AgentStepBuilder {

        private final StepBuilder parent;

        AgentStepBuilder(StepBuilder parent) { this.parent = parent; }

        public AgentStepBuilder instructions(String instructions) { parent.instructions = instructions; return this; }
        public AgentStepBuilder dependencies(String... deps) { parent.dependencies = List.of(deps); return this; }
        public AgentStepBuilder dependencies(List<String> deps) { parent.dependencies = deps; return this; }
        public AgentStepBuilder hitl(boolean hitl) { parent.hitl = hitl; return this; }
        public AgentStepBuilder hitl() { parent.hitl = true; return this; }
        public AgentStepBuilder skills(String... skills) { parent.skills = List.of(skills); return this; }
        public AgentStepBuilder skills(List<String> skills) { parent.skills = skills; return this; }
        public AgentStepBuilder tools(String... tools) { parent.tools = List.of(tools); return this; }
        public AgentStepBuilder tools(List<String> tools) { parent.tools = tools; return this; }
    }

    public static class CodeStepBuilder {

        private final StepBuilder parent;

        CodeStepBuilder(StepBuilder parent) { this.parent = parent; }

        public <I> CodeStepBuilder input(I input) { parent.input = input; return this; }
        public CodeStepBuilder dependencies(String... deps) { parent.dependencies = List.of(deps); return this; }
        public CodeStepBuilder dependencies(List<String> deps) { parent.dependencies = deps; return this; }
    }

    public static class LoopBuilder {

        private final String name;
        private String over;
        private List<String> dependencies = List.of();
        private boolean hitl;
        private final List<PlanStepConfig> steps = new ArrayList<>();

        LoopBuilder(String name) { this.name = name; }

        public LoopBuilder over(String stepName) { this.over = stepName; return this; }
        public LoopBuilder dependencies(String... deps) { this.dependencies = List.of(deps); return this; }
        public LoopBuilder dependencies(List<String> deps) { this.dependencies = deps; return this; }
        public LoopBuilder hitl(boolean hitl) { this.hitl = hitl; return this; }
        public LoopBuilder hitl() { this.hitl = true; return this; }

        public LoopBuilder step(PlanStepConfig step) { steps.add(step); return this; }
        public LoopBuilder step(String name, Consumer<StepBuilder> config) {
            var b = new StepBuilder(name);
            config.accept(b);
            steps.add(b.build());
            return this;
        }

        PlanStepConfig build() {

            return new PlanStepConfig(name, "loop", null, null, dependencies, hitl, null, null,
                    over, null, null, null, steps, null, null);
        }
    }

    public static class BranchBuilder {

        private final String name;
        private String from;
        private String defaultPath;
        private List<String> dependencies = List.of();
        private boolean hitl;
        private final List<BranchPathConfig> pathConfigs = new ArrayList<>();

        BranchBuilder(String name) { this.name = name; }

        public BranchBuilder from(String stepName) { this.from = stepName; return this; }
        public BranchBuilder defaultPath(String pathName) { this.defaultPath = pathName; return this; }
        public BranchBuilder dependencies(String... deps) { this.dependencies = List.of(deps); return this; }
        public BranchBuilder dependencies(List<String> deps) { this.dependencies = deps; return this; }
        public BranchBuilder hitl(boolean hitl) { this.hitl = hitl; return this; }
        public BranchBuilder hitl() { this.hitl = true; return this; }

        public BranchBuilder path(BranchPathConfig path) { pathConfigs.add(path); return this; }
        public BranchBuilder path(String pathName, Consumer<PathBuilder> config) {
            var b = new PathBuilder(pathName);
            config.accept(b);
            pathConfigs.add(b.build());
            return this;
        }

        PlanStepConfig build() {

            return new PlanStepConfig(name, "branch", null, null, dependencies, hitl, null, null,
                    null, from, pathConfigs, defaultPath, null, null, null);
        }
    }

    public static class PathBuilder {

        private final String pathName;
        private String agent;
        private String instructions;
        private List<String> tools = List.of();
        private final List<PlanStepConfig> steps = new ArrayList<>();

        PathBuilder(String pathName) { this.pathName = pathName; }

        public PathBuilder agent(String agent) { this.agent = agent; return this; }
        public PathBuilder instructions(String instructions) { this.instructions = instructions; return this; }
        public PathBuilder tools(String... tools) { this.tools = List.of(tools); return this; }
        public PathBuilder tools(List<String> tools) { this.tools = tools; return this; }

        public PathBuilder step(PlanStepConfig step) { steps.add(step); return this; }
        public PathBuilder step(String name, Consumer<StepBuilder> config) {
            var b = new StepBuilder(name);
            config.accept(b);
            steps.add(b.build());
            return this;
        }

        BranchPathConfig build() {

            return new BranchPathConfig(pathName, agent, instructions, tools,
                    steps.isEmpty() ? null : steps);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PlanParamConfig(
            String name,
            String description,
            String defaultValue,
            boolean required) {

        public PlanParamConfig {

            if (name == null || name.isBlank())
                throw new IllegalArgumentException("Parameter name is required");
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PlanStepConfig(
            String name,
            String type,
            String agent,
            String instructions,
            List<String> dependencies,
            boolean hitl,
            List<String> skills,
            List<String> tools,
            String over,
            String from,
            List<BranchPathConfig> pathConfigs,
            String defaultPath,
            List<PlanStepConfig> steps,
            String codeSlug,
            Object codeInput) {

        public PlanStepConfig {

            if (name == null || name.isBlank())
                throw new IllegalArgumentException("Step name is required");

            if (type == null)
                type = "agent";

            if (dependencies == null)
                dependencies = List.of();

            if (skills == null)
                skills = List.of();

            if (tools == null)
                tools = List.of();
        }

        public PlanStep toPlanStep() {

            return toPlanStep(null);
        }

        public PlanStep toPlanStep(CodeStepRegistry codeStepRegistry) {

            return switch (type) {

                case "loop" -> {

                    List<PlanStep> resolvedSteps;

                    if (steps != null && !steps.isEmpty()) {

                        resolvedSteps = steps.stream().map(s -> s.toPlanStep(codeStepRegistry)).toList();
                    }
                    else {

                        var stepName = name + "-body";

                        var step = new PlanStepAgent(stepName, agent, instructions, List.of(), false, skills, tools);

                        resolvedSteps = List.of(step);
                    }

                    yield new PlanStepLoop(name, over, resolvedSteps, dependencies, hitl);
                }

                case "branch" -> {

                    var paths = this.pathConfigs.stream()
                            .map(bpc -> new PlanStepBranch.Path(bpc.pathName(), bpc.toPlanStep(codeStepRegistry)))
                            .toList();

                    yield new PlanStepBranch(name, from, paths, defaultPath, dependencies, hitl);
                }

                case "code" -> buildCodeStep(codeStepRegistry);

                default -> new PlanStepAgent(name, agent, instructions, dependencies, hitl, skills, tools);
            };
        }

        private PlanStepCode<?> buildCodeStep(CodeStepRegistry codeStepRegistry) {

            Object typedInput = codeInput;

            if (codeInput != null && codeStepRegistry != null) {

                var registered = codeStepRegistry.get(codeSlug);

                if (registered != null) {

                    var inputType = registered.spec().inputType();

                    if (inputType != Void.class && !inputType.isInstance(codeInput))
                        typedInput = Json.mapper().convertValue(codeInput, inputType);
                }
            }

            return new PlanStepCode<>(name, codeSlug, typedInput, dependencies);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record BranchPathConfig(
            String pathName,
            String agent,
            String instructions,
            List<String> tools,
            List<PlanStepConfig> steps) {

        public BranchPathConfig {

            if (pathName == null || pathName.isBlank())
                throw new IllegalArgumentException("Path name is required");

            if (tools == null)
                tools = List.of();
        }

        List<PlanStep> toPlanStep() {

            return toPlanStep(null);
        }

        List<PlanStep> toPlanStep(CodeStepRegistry codeStepRegistry) {

            if (steps != null && !steps.isEmpty())
                return steps.stream().map(s -> s.toPlanStep(codeStepRegistry)).toList();

            var stepName = pathName + "-body";

            var agentStep =
                    new PlanStepAgent(stepName, agent, instructions, List.of(), false, List.of(), tools);

            return List.of(agentStep);
        }
    }
}
