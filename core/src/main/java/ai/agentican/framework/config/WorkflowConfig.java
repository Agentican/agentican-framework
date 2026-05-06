package ai.agentican.framework.config;

import ai.agentican.framework.orchestration.code.CodeStepRegistry;
import ai.agentican.framework.orchestration.model.WorkflowStepAgent;
import ai.agentican.framework.orchestration.model.WorkflowStepBranch;
import ai.agentican.framework.orchestration.model.WorkflowStepCode;
import ai.agentican.framework.orchestration.model.WorkflowStepLoop;
import ai.agentican.framework.orchestration.model.WorkflowDefinition;
import ai.agentican.framework.orchestration.model.WorkflowStep;
import ai.agentican.framework.orchestration.model.WorkflowParam;
import ai.agentican.framework.util.Json;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

@JsonIgnoreProperties(ignoreUnknown = true)
public record  WorkflowConfig(
        String name,
        String description,
        List<PlanParamConfig> params,
        List<PlanStepConfig> steps,
        String outputStep) {

    public WorkflowConfig {

        if (name == null || name.isBlank())
            throw new IllegalArgumentException("Task name is required");

        if (steps == null)
            steps = List.of();

        if (params == null)
            params = List.of();

        if (outputStep != null && outputStep.isBlank())
            outputStep = null;
    }

    public WorkflowDefinition toDefinition() {

        return toDefinition(null);
    }

    public WorkflowDefinition toDefinition(CodeStepRegistry codeStepRegistry) {

        var builder = WorkflowDefinition.builder(name)
                .description(description)
                .outputStep(outputStep);

        params.forEach(p -> builder.param(p.toWorkflowParam()));

        steps.forEach(s -> builder.step(s.toWorkflowStep(codeStepRegistry)));

        return builder.build();
    }

    public static WorkflowConfigBuilder builder() {

        return new WorkflowConfigBuilder();
    }

    public static class WorkflowConfigBuilder {

        private String name;
        private String description;

        private final List<PlanParamConfig> params = new ArrayList<>();
        private final List<PlanStepConfig> steps = new ArrayList<>();

        private String outputStep;

        public WorkflowConfigBuilder name(String name) { this.name = name; return this; }
        public WorkflowConfigBuilder description(String description) { this.description = description; return this; }
        public WorkflowConfigBuilder outputStep(String stepName) { this.outputStep = stepName; return this; }
        public WorkflowConfigBuilder param(PlanParamConfig param) { this.params.add(param); return this; }
        public WorkflowConfigBuilder step(PlanStepConfig step) { this.steps.add(step); return this; }
        public WorkflowConfigBuilder steps(List<PlanStepConfig> steps) { this.steps.addAll(steps); return this; }

        public WorkflowConfigBuilder param(String name, String description, String defaultValue, boolean required) {

            this.params.add(new PlanParamConfig(name, description, defaultValue, required));

            return this;
        }

        public WorkflowConfigBuilder step(String name, Consumer<StepBuilder> config) {

            var builder = new StepBuilder(name);

            config.accept(builder);

            this.steps.add(builder.build());

            return this;
        }

        public WorkflowConfigBuilder loop(String name, Consumer<LoopBuilder> config) {

            var builder = new LoopBuilder(name);

            config.accept(builder);

            this.steps.add(builder.build());

            return this;
        }

        public WorkflowConfigBuilder branch(String name, Consumer<BranchBuilder> config) {

            var builder = new BranchBuilder(name);

            config.accept(builder);

            this.steps.add(builder.build());

            return this;
        }

        public WorkflowConfig build() {

            return new WorkflowConfig(name, description, params, steps, outputStep);
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

        PlanStepConfig build() {

            if (agent != null)
                return new PlanStepConfig(name, "agent", agent, instructions, dependencies, hitl, skills, tools,
                        null, null, null, null, null, null, null);

            if (codeSlug != null)
                return new PlanStepConfig(name, "code", null, null, dependencies, false, null,
                        null, null, null, null, null, null, codeSlug, input);

            throw new IllegalStateException(
                    "Step '" + name + "' must declare either .agent(\"...\") or .code(\"slug\")");
        }

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

            var builder = new StepBuilder(name);

            config.accept(builder);

            steps.add(builder.build());

            return this;
        }

        PlanStepConfig build() {

            return new PlanStepConfig(name, "loop", null, null, dependencies, hitl, null,
                    null, over, null, null, null, steps, null, null);
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

            var builder = new PathBuilder(pathName);

            config.accept(builder);

            pathConfigs.add(builder.build());

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
        private List<String> skills = List.of();
        private final List<PlanStepConfig> steps = new ArrayList<>();

        PathBuilder(String pathName) { this.pathName = pathName; }

        public PathBuilder agent(String agent) { this.agent = agent; return this; }
        public PathBuilder instructions(String instructions) { this.instructions = instructions; return this; }
        public PathBuilder tools(String... tools) { this.tools = List.of(tools); return this; }
        public PathBuilder tools(List<String> tools) { this.tools = tools; return this; }
        public PathBuilder skills(String... skills) { this.skills = List.of(skills); return this; }
        public PathBuilder skills(List<String> skills) { this.skills = skills; return this; }
        public PathBuilder step(PlanStepConfig step) { steps.add(step); return this; }

        public PathBuilder step(String name, Consumer<StepBuilder> config) {

            var builder = new StepBuilder(name);

            config.accept(builder);

            steps.add(builder.build());

            return this;
        }

        BranchPathConfig build() {

            return new BranchPathConfig(pathName, agent, instructions, tools, skills, steps.isEmpty() ? null : steps);
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

        public WorkflowParam toWorkflowParam() {

            return new WorkflowParam(name, description, defaultValue, required);
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

        public WorkflowStep toWorkflowStep() {

            return toWorkflowStep(null);
        }

        public WorkflowStep toWorkflowStep(CodeStepRegistry codeStepRegistry) {

            return switch (type) {

                case "loop" -> {

                    List<WorkflowStep> resolvedSteps;

                    if (steps != null && !steps.isEmpty()) {

                        resolvedSteps = steps.stream().map(s -> s.toWorkflowStep(codeStepRegistry)).toList();
                    }
                    else {

                        var stepName = name + "-body";

                        var step = new WorkflowStepAgent(stepName, agent, instructions, List.of(), false, skills, tools);

                        resolvedSteps = List.of(step);
                    }

                    yield new WorkflowStepLoop(name, over, resolvedSteps, dependencies, hitl);
                }

                case "branch" -> {

                    var paths = this.pathConfigs.stream()
                            .map(bpc -> new WorkflowStepBranch.Path(bpc.pathName(), bpc.toPlanStep(codeStepRegistry)))
                            .toList();

                    yield new WorkflowStepBranch(name, from, paths, defaultPath, dependencies, hitl);
                }

                case "code" -> buildCodeStep(codeStepRegistry);

                default -> new WorkflowStepAgent(name, agent, instructions, dependencies, hitl, skills, tools);
            };
        }

        private WorkflowStepCode<?> buildCodeStep(CodeStepRegistry codeStepRegistry) {

            Object typedInput = codeInput;

            if (codeInput != null && codeStepRegistry != null) {

                var registered = codeStepRegistry.get(codeSlug);

                if (registered != null) {

                    var inputType = registered.spec().inputType();

                    if (inputType != Void.class && !inputType.isInstance(codeInput))
                        typedInput = Json.mapper().convertValue(codeInput, inputType);
                }
            }

            return new WorkflowStepCode<>(name, codeSlug, typedInput, dependencies);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record BranchPathConfig(
            String pathName,
            String agent,
            String instructions,
            List<String> tools,
            List<String> skills,
            List<PlanStepConfig> steps) {

        public BranchPathConfig {

            if (pathName == null || pathName.isBlank())
                throw new IllegalArgumentException("Path name is required");

            if (tools == null)
                tools = List.of();

            if (skills == null)
                skills = List.of();
        }

        List<WorkflowStep> toPlanStep() {

            return toPlanStep(null);
        }

        List<WorkflowStep> toPlanStep(CodeStepRegistry codeStepRegistry) {

            if (steps != null && !steps.isEmpty())
                return steps.stream().map(s -> s.toWorkflowStep(codeStepRegistry)).toList();

            var stepName = pathName + "-body";

            var agentStep = new WorkflowStepAgent(stepName, agent, instructions, List.of(), false, skills, tools);

            return List.of(agentStep);
        }
    }
}
