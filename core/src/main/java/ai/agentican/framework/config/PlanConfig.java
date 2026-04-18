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
        List<PlanParamConfig> paramConfigs,
        List<PlanStepConfig> stepConfigs,
        String externalId) {

    public PlanConfig {

        if (name == null || name.isBlank())
            throw new IllegalArgumentException("Task name is required");

        if (stepConfigs == null)
            stepConfigs = List.of();

        if (paramConfigs == null)
            paramConfigs = List.of();

        if (externalId != null && externalId.isBlank())
            externalId = null;
    }

    public PlanConfig(String name, String description, List<PlanParamConfig> paramConfigs,
                      List<PlanStepConfig> stepConfigs) {

        this(name, description, paramConfigs, stepConfigs, null);
    }

    public static PlanConfigBuilder builder() {

        return new PlanConfigBuilder();
    }

    public Plan toPlan() {

        return toPlan(null);
    }

    public Plan toPlan(CodeStepRegistry codeStepRegistry) {

        var params = paramConfigs.stream().map(tpc ->
                        PlanParam.of(tpc.name(), tpc.description(), tpc.defaultValue(), tpc.required()))
                .toList();

        var steps = stepConfigs.stream().map(s -> s.toPlanStep(codeStepRegistry)).toList();

        return Plan.withExternalId(externalId, name, description, params, steps);
    }

    public static class PlanConfigBuilder {

        private String name;
        private String description;
        private String externalId;

        private final List<PlanParamConfig> paramConfigs = new ArrayList<>();
        private final List<PlanStepConfig> stepConfigs = new ArrayList<>();

        public PlanConfigBuilder name(String name) { this.name = name; return this; }
        public PlanConfigBuilder description(String description) { this.description = description; return this; }
        public PlanConfigBuilder externalId(String externalId) { this.externalId = externalId; return this; }

        public PlanConfigBuilder param(PlanParamConfig param) { this.paramConfigs.add(param); return this; }
        public PlanConfigBuilder param(String name, String description, String defaultValue, boolean required) {
            this.paramConfigs.add(new PlanParamConfig(name, description, defaultValue, required));
            return this;
        }

        public PlanConfigBuilder step(PlanStepConfig step) { this.stepConfigs.add(step); return this; }
        public PlanConfigBuilder steps(List<PlanStepConfig> steps) { this.stepConfigs.addAll(steps); return this; }

        public PlanConfigBuilder step(String name, Consumer<StepBuilder> config) {
            var b = new StepBuilder(name);
            config.accept(b);
            this.stepConfigs.add(b.build());
            return this;
        }

        public PlanConfigBuilder loop(String name, Consumer<LoopBuilder> config) {
            var b = new LoopBuilder(name);
            config.accept(b);
            this.stepConfigs.add(b.build());
            return this;
        }

        public PlanConfigBuilder branch(String name, Consumer<BranchBuilder> config) {
            var b = new BranchBuilder(name);
            config.accept(b);
            this.stepConfigs.add(b.build());
            return this;
        }

        public PlanConfigBuilder codeStep(String name, Consumer<CodeStepBuilder> config) {
            var b = new CodeStepBuilder(name);
            config.accept(b);
            this.stepConfigs.add(b.build());
            return this;
        }

        public PlanConfig build() {

            return new PlanConfig(name, description, paramConfigs, stepConfigs, externalId);
        }
    }

    public static class StepBuilder {

        private final String name;
        private String agent;
        private String instructions;
        private List<String> dependencies = List.of();
        private boolean hitl;
        private List<String> skills = List.of();
        private List<String> tools = List.of();

        StepBuilder(String name) { this.name = name; }

        public StepBuilder agent(String agent) { this.agent = agent; return this; }
        public StepBuilder instructions(String instructions) { this.instructions = instructions; return this; }
        public StepBuilder dependencies(String... deps) { this.dependencies = List.of(deps); return this; }
        public StepBuilder dependencies(List<String> deps) { this.dependencies = deps; return this; }
        public StepBuilder hitl(boolean hitl) { this.hitl = hitl; return this; }
        public StepBuilder hitl() { this.hitl = true; return this; }
        public StepBuilder skills(String... skills) { this.skills = List.of(skills); return this; }
        public StepBuilder skills(List<String> skills) { this.skills = skills; return this; }
        public StepBuilder tools(String... tools) { this.tools = List.of(tools); return this; }
        public StepBuilder tools(List<String> tools) { this.tools = tools; return this; }

        PlanStepConfig build() {

            return new PlanStepConfig(name, "agent", agent, instructions, dependencies, hitl, skills, tools,
                    null, null, null, null, null, null, null);
        }
    }

    public static class CodeStepBuilder {

        private final String name;
        private String codeSlug;
        private Object inputs;
        private List<String> dependencies = List.of();

        CodeStepBuilder(String name) { this.name = name; }

        public CodeStepBuilder code(String slug) { this.codeSlug = slug; return this; }
        public <I> CodeStepBuilder input(I input) { this.inputs = input; return this; }
        public CodeStepBuilder dependencies(String... deps) { this.dependencies = List.of(deps); return this; }
        public CodeStepBuilder dependencies(List<String> deps) { this.dependencies = deps; return this; }

        PlanStepConfig build() {

            return new PlanStepConfig(name, "code", null, null, dependencies, false, null, null,
                    null, null, null, null, null, codeSlug, inputs);
        }
    }

    public static class LoopBuilder {

        private final String name;
        private String over;
        private List<String> dependencies = List.of();
        private boolean hitl;
        private final List<PlanStepConfig> stepConfigs = new ArrayList<>();

        LoopBuilder(String name) { this.name = name; }

        public LoopBuilder over(String stepName) { this.over = stepName; return this; }
        public LoopBuilder dependencies(String... deps) { this.dependencies = List.of(deps); return this; }
        public LoopBuilder dependencies(List<String> deps) { this.dependencies = deps; return this; }
        public LoopBuilder hitl(boolean hitl) { this.hitl = hitl; return this; }
        public LoopBuilder hitl() { this.hitl = true; return this; }

        public LoopBuilder step(PlanStepConfig step) { stepConfigs.add(step); return this; }
        public LoopBuilder step(String name, Consumer<StepBuilder> config) {
            var b = new StepBuilder(name);
            config.accept(b);
            stepConfigs.add(b.build());
            return this;
        }

        PlanStepConfig build() {

            return new PlanStepConfig(name, "loop", null, null, dependencies, hitl, null, null,
                    over, null, null, null, stepConfigs, null, null);
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
        private final List<PlanStepConfig> stepConfigs = new ArrayList<>();

        PathBuilder(String pathName) { this.pathName = pathName; }

        public PathBuilder agent(String agent) { this.agent = agent; return this; }
        public PathBuilder instructions(String instructions) { this.instructions = instructions; return this; }
        public PathBuilder tools(String... tools) { this.tools = List.of(tools); return this; }
        public PathBuilder tools(List<String> tools) { this.tools = tools; return this; }

        public PathBuilder step(PlanStepConfig step) { stepConfigs.add(step); return this; }
        public PathBuilder step(String name, Consumer<StepBuilder> config) {
            var b = new StepBuilder(name);
            config.accept(b);
            stepConfigs.add(b.build());
            return this;
        }

        BranchPathConfig build() {

            return new BranchPathConfig(pathName, agent, instructions, tools,
                    stepConfigs.isEmpty() ? null : stepConfigs);
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
            List<PlanStepConfig> stepConfigs,
            String codeSlug,
            Object codeInputs) {

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

                    List<PlanStep> steps;

                    if (stepConfigs != null && !stepConfigs.isEmpty()) {

                        steps = stepConfigs.stream().map(s -> s.toPlanStep(codeStepRegistry)).toList();
                    }
                    else {

                        var stepName = name + "-body";

                        var step = PlanStepAgent.of(stepName, agent, instructions, List.of(), false, skills, tools);

                        steps = List.of(step);
                    }

                    yield new PlanStepLoop(name, over, steps, dependencies, hitl);
                }

                case "branch" -> {

                    var paths = this.pathConfigs.stream()
                            .map(bpc -> PlanStepBranch.Path.of(bpc.pathName(), bpc.toPlanStep(codeStepRegistry)))
                            .toList();

                    yield PlanStepBranch.of(name, from, paths, defaultPath, dependencies, hitl);
                }

                case "code" -> buildCodeStep(codeStepRegistry);

                default -> PlanStepAgent.of(name, agent, instructions, dependencies, hitl, skills, tools);
            };
        }

        private PlanStepCode<?> buildCodeStep(CodeStepRegistry codeStepRegistry) {

            Object typedInputs = codeInputs;

            if (codeInputs != null && codeStepRegistry != null) {

                var registered = codeStepRegistry.get(codeSlug);

                if (registered != null) {

                    var inputType = registered.spec().inputType();

                    if (inputType != Void.class && !inputType.isInstance(codeInputs))
                        typedInputs = Json.mapper().convertValue(codeInputs, inputType);
                }
            }

            return PlanStepCode.of(name, codeSlug, typedInputs, dependencies);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record BranchPathConfig(
            String pathName,
            String agent,
            String instructions,
            List<String> tools,
            List<PlanStepConfig> stepConfigs) {

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

            if (stepConfigs != null && !stepConfigs.isEmpty())
                return stepConfigs.stream().map(s -> s.toPlanStep(codeStepRegistry)).toList();

            var stepName = pathName + "-body";

            var agentStep =
                    PlanStepAgent.of(stepName, agent, instructions, List.of(), false, List.of(), tools);

            return List.of(agentStep);
        }
    }
}
