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
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record WorkflowConfig(
        String id,
        String name,
        String description,
        List<PlanParamConfig> params,
        List<PlanStepConfig> steps,
        String outputStep) {

    public WorkflowConfig {

        if (id == null || id.isBlank())
            throw new IllegalArgumentException("Workflow id is required (name='" + name + "')");

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

        var resolvedSteps = steps.stream().map(s -> s.toWorkflowStep(codeStepRegistry)).toList();

        return WorkflowDefinition.builder(id, name)
                .description(description)
                .outputStep(outputStep)
                .params(params.stream().map(PlanParamConfig::toWorkflowParam).toList())
                .steps(resolvedSteps)
                .build();
    }

    public static WorkflowConfigBuilder builder() {

        return new WorkflowConfigBuilder();
    }

    public static class WorkflowConfigBuilder {

        private String id;
        private String name;
        private String description;

        private final List<PlanParamConfig> params = new ArrayList<>();
        private final List<PlanStepConfig> steps = new ArrayList<>();

        private String outputStep;

        public WorkflowConfigBuilder id(String id)                          { this.id = id; return this; }
        public WorkflowConfigBuilder name(String name)                      { this.name = name; return this; }
        public WorkflowConfigBuilder description(String description)        { this.description = description; return this; }
        public WorkflowConfigBuilder outputStep(String stepName)            { this.outputStep = stepName; return this; }

        public ParamEntry param()   { return new ParamEntry(); }
        public StepEntry<WorkflowConfigBuilder> step() { return new StepEntry<>(this, steps); }
        public LoopEntry loop()     { return new LoopEntry(); }
        public BranchEntry branch() { return new BranchEntry(); }

        public WorkflowConfig build() {

            return new WorkflowConfig(id, name, description, params, steps, outputStep);
        }

        public final class ParamEntry {

            private String paramName;
            private String description;
            private String defaultValue;
            private boolean required;

            ParamEntry() {}

            public ParamEntry name(String name)                 { this.paramName = name; return this; }
            public ParamEntry description(String description)   { this.description = description; return this; }
            public ParamEntry defaultValue(String defaultValue) { this.defaultValue = defaultValue; return this; }
            public ParamEntry required(boolean required)        { this.required = required; return this; }

            public WorkflowConfigBuilder end() {

                params.add(new PlanParamConfig(paramName, description, defaultValue, required));

                return WorkflowConfigBuilder.this;
            }
        }

        public final class LoopEntry {

            private String loopName;
            private String over;
            private List<String> dependencies = List.of();
            private boolean hitl;

            private final List<PlanStepConfig> bodySteps = new ArrayList<>();

            LoopEntry() {}

            public LoopEntry name(String name)                  { this.loopName = name; return this; }
            public LoopEntry over(String stepName)              { this.over = stepName; return this; }
            public LoopEntry dependencies(String... deps)       { this.dependencies = List.of(deps); return this; }
            public LoopEntry dependencies(List<String> deps)    { this.dependencies = deps; return this; }
            public LoopEntry hitl(boolean hitl)                 { this.hitl = hitl; return this; }
            public LoopEntry hitl()                             { this.hitl = true; return this; }

            public StepEntry<LoopEntry> step() { return new StepEntry<>(this, bodySteps); }

            public WorkflowConfigBuilder end() {

                steps.add(new PlanStepConfig(loopName, "loop", null, null, dependencies, hitl, null,
                        null, over, null, null, null, bodySteps, null, null));

                return WorkflowConfigBuilder.this;
            }
        }

        public final class BranchEntry {

            private String branchStepName;
            private String from;
            private String defaultBranch;
            private List<String> dependencies = List.of();
            private boolean hitl;

            private final List<BranchConfig> branches = new ArrayList<>();

            BranchEntry() {}

            public BranchEntry name(String name)                { this.branchStepName = name; return this; }
            public BranchEntry from(String stepName)            { this.from = stepName; return this; }
            public BranchEntry defaultBranch(String branchName) { this.defaultBranch = branchName; return this; }
            public BranchEntry dependencies(String... deps)     { this.dependencies = List.of(deps); return this; }
            public BranchEntry dependencies(List<String> deps)  { this.dependencies = deps; return this; }
            public BranchEntry hitl(boolean hitl)               { this.hitl = hitl; return this; }
            public BranchEntry hitl()                           { this.hitl = true; return this; }

            public Branch branch() { return new Branch(); }

            public WorkflowConfigBuilder end() {

                steps.add(new PlanStepConfig(branchStepName, "branch", null, null, dependencies, hitl, null, null,
                        null, from, branches, defaultBranch, null, null, null));

                return WorkflowConfigBuilder.this;
            }

            public final class Branch {

                private String branchName;

                private final List<PlanStepConfig> bodySteps = new ArrayList<>();

                Branch() {}

                public Branch name(String name) { this.branchName = name; return this; }

                public StepEntry<Branch> step() { return new StepEntry<>(this, bodySteps); }

                public BranchEntry end() {

                    branches.add(new BranchConfig(branchName, bodySteps));

                    return BranchEntry.this;
                }
            }
        }
    }

    /** Generic step entry — discriminates via {@code .agent(...)} / {@code .code(...)}. */
    public static final class StepEntry<P> {

        private final P parent;
        private final List<PlanStepConfig> sink;

        private String stepName;

        StepEntry(P parent, List<PlanStepConfig> sink) {

            this.parent = parent;
            this.sink = sink;
        }

        public StepEntry<P> name(String name) { this.stepName = name; return this; }

        public AgentStepEntry<P> agent(String agent) {

            return new AgentStepEntry<>(parent, sink, stepName, agent);
        }

        public CodeStepEntry<P> code(String slug) {

            return new CodeStepEntry<>(parent, sink, stepName, slug);
        }
    }

    public static final class AgentStepEntry<P> {

        private final P parent;
        private final List<PlanStepConfig> sink;

        private final String stepName;
        private final String agent;

        private String instructions;
        private List<String> dependencies = List.of();
        private List<String> skills = List.of();
        private List<String> tools = List.of();
        private boolean hitl;

        AgentStepEntry(P parent, List<PlanStepConfig> sink, String stepName, String agent) {

            this.parent = parent;
            this.sink = sink;
            this.stepName = stepName;
            this.agent = agent;
        }

        public AgentStepEntry<P> instructions(String instructions) { this.instructions = instructions; return this; }
        public AgentStepEntry<P> dependencies(String... deps)      { this.dependencies = List.of(deps); return this; }
        public AgentStepEntry<P> dependencies(List<String> deps)   { this.dependencies = deps; return this; }
        public AgentStepEntry<P> skills(String... skills)          { this.skills = List.of(skills); return this; }
        public AgentStepEntry<P> skills(List<String> skills)       { this.skills = skills; return this; }
        public AgentStepEntry<P> tools(String... tools)            { this.tools = List.of(tools); return this; }
        public AgentStepEntry<P> tools(List<String> tools)         { this.tools = tools; return this; }
        public AgentStepEntry<P> hitl(boolean hitl)                { this.hitl = hitl; return this; }
        public AgentStepEntry<P> hitl()                            { this.hitl = true; return this; }

        public P end() {

            sink.add(new PlanStepConfig(stepName, "agent", agent, instructions, dependencies, hitl, skills, tools,
                    null, null, null, null, null, null, null));

            return parent;
        }
    }

    public static final class CodeStepEntry<P> {

        private final P parent;
        private final List<PlanStepConfig> sink;

        private final String stepName;
        private final String slug;

        private Object input;
        private List<String> dependencies = List.of();

        CodeStepEntry(P parent, List<PlanStepConfig> sink, String stepName, String slug) {

            this.parent = parent;
            this.sink = sink;
            this.stepName = stepName;
            this.slug = slug;
        }

        public <I> CodeStepEntry<P> input(I input)              { this.input = input; return this; }
        public CodeStepEntry<P> dependencies(String... deps)    { this.dependencies = List.of(deps); return this; }
        public CodeStepEntry<P> dependencies(List<String> deps) { this.dependencies = deps; return this; }

        public P end() {

            sink.add(new PlanStepConfig(stepName, "code", null, null, dependencies, false, null,
                    null, null, null, null, null, null, slug, input));

            return parent;
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
            List<BranchConfig> branches,
            @JsonProperty("default") String defaultBranch,
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

                    var resolvedBranches = this.branches.stream()
                            .map(b -> new WorkflowStepBranch.Branch(b.name(), b.toPlanSteps(codeStepRegistry)))
                            .toList();

                    yield new WorkflowStepBranch(name, from, resolvedBranches, defaultBranch, dependencies, hitl);
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
    public record BranchConfig(
            String name,
            List<PlanStepConfig> steps) {

        public BranchConfig {

            if (name == null || name.isBlank())
                throw new IllegalArgumentException("Branch name is required");

            if (steps == null || steps.isEmpty())
                throw new IllegalArgumentException("Steps are required for branch '" + name + "'");

            steps = List.copyOf(steps);
        }

        List<WorkflowStep> toPlanSteps() {

            return toPlanSteps(null);
        }

        List<WorkflowStep> toPlanSteps(CodeStepRegistry codeStepRegistry) {

            return steps.stream().map(s -> s.toWorkflowStep(codeStepRegistry)).toList();
        }
    }
}
