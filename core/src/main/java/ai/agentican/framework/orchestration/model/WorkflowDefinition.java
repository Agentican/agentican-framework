package ai.agentican.framework.orchestration.model;

import java.util.ArrayList;
import java.util.List;

public record WorkflowDefinition(
        String id,
        String name,
        String description,
        List<WorkflowParam> params,
        List<WorkflowStep> steps,
        String outputStep) {

    public WorkflowDefinition {

        if (id == null || id.isBlank())
            throw new IllegalArgumentException("WorkflowDefinition id is required (name='" + name + "')");

        if (name == null || name.isBlank())
            throw new IllegalArgumentException("WorkflowDefinition name is required");

        if (steps == null || steps.isEmpty())
            throw new IllegalArgumentException("WorkflowDefinition must have at least one node");

        if (params == null)
            params = List.of();

        params = List.copyOf(params);
        steps = List.copyOf(steps);

        if (outputStep != null && outputStep.isBlank())
            outputStep = null;
    }

    public static Builder builder(String id, String name) {

        return new Builder(id, name);
    }

    public static class Builder {

        private final String id;
        private final String name;

        private final List<WorkflowParam> params = new ArrayList<>();
        private final List<WorkflowStep> steps = new ArrayList<>();

        private String description;
        private String outputStep;

        Builder(String id, String name) {

            this.id = id;
            this.name = name;
        }

        public Builder description(String description) { this.description = description; return this; }
        public Builder outputStep(String stepName) { this.outputStep = stepName; return this; }

        public ParamEntry param() { return new ParamEntry(); }
        public StepEntry<Builder> step() { return new StepEntry<>(this, steps); }
        public LoopEntry loop() { return new LoopEntry(); }
        public BranchEntry branch() { return new BranchEntry(); }

        /** Bulk-add pre-resolved steps. Used by the runtime when materialising loop iterations or branch paths. */
        public Builder steps(List<WorkflowStep> bodySteps) { this.steps.addAll(bodySteps); return this; }

        /** Bulk-add pre-resolved params. Used by {@code WorkflowConfig.toDefinition} and equivalent loaders. */
        public Builder params(List<WorkflowParam> planParams) { this.params.addAll(planParams); return this; }

        public WorkflowDefinition build() {

            return new WorkflowDefinition(id, name, description, params, steps, outputStep);
        }

        public final class ParamEntry {

            private String paramName;
            private String description;
            private String defaultValue;
            private boolean required;

            ParamEntry() {}

            public ParamEntry name(String name)                { this.paramName = name; return this; }
            public ParamEntry description(String description)  { this.description = description; return this; }
            public ParamEntry defaultValue(String defaultValue) { this.defaultValue = defaultValue; return this; }
            public ParamEntry required(boolean required)       { this.required = required; return this; }

            public Builder end() {

                params.add(new WorkflowParam(paramName, description, defaultValue, required));

                return Builder.this;
            }
        }

        public final class LoopEntry {

            private String loopName;
            private String over;
            private List<String> dependencies = List.of();
            private boolean hitl;

            private final List<WorkflowStep> bodySteps = new ArrayList<>();

            LoopEntry() {}

            public LoopEntry name(String name)                  { this.loopName = name; return this; }
            public LoopEntry over(String stepName)              { this.over = stepName; return this; }
            public LoopEntry dependencies(String... deps)       { this.dependencies = List.of(deps); return this; }
            public LoopEntry dependencies(List<String> deps)    { this.dependencies = deps; return this; }
            public LoopEntry hitl(boolean hitl)                 { this.hitl = hitl; return this; }
            public LoopEntry hitl()                             { this.hitl = true; return this; }

            public StepEntry<LoopEntry> step() { return new StepEntry<>(this, bodySteps); }

            public Builder end() {

                steps.add(new WorkflowStepLoop(loopName, over, bodySteps, dependencies, hitl));

                return Builder.this;
            }
        }

        public final class BranchEntry {

            private String branchStepName;
            private String from;
            private String defaultBranch;
            private List<String> dependencies = List.of();
            private boolean hitl;

            private final List<WorkflowStepBranch.Branch> branches = new ArrayList<>();

            BranchEntry() {}

            public BranchEntry name(String name)                { this.branchStepName = name; return this; }
            public BranchEntry from(String stepName)            { this.from = stepName; return this; }
            public BranchEntry defaultBranch(String branchName) { this.defaultBranch = branchName; return this; }
            public BranchEntry dependencies(String... deps)     { this.dependencies = List.of(deps); return this; }
            public BranchEntry dependencies(List<String> deps)  { this.dependencies = deps; return this; }
            public BranchEntry hitl(boolean hitl)               { this.hitl = hitl; return this; }
            public BranchEntry hitl()                           { this.hitl = true; return this; }

            public Branch branch() { return new Branch(); }

            public Builder end() {

                steps.add(new WorkflowStepBranch(branchStepName, from, branches, defaultBranch, dependencies, hitl));

                return Builder.this;
            }

            public final class Branch {

                private String branchName;
                private final List<WorkflowStep> bodySteps = new ArrayList<>();

                Branch() {}

                public Branch name(String name) { this.branchName = name; return this; }

                public StepEntry<Branch> step() { return new StepEntry<>(this, bodySteps); }

                public BranchEntry end() {

                    branches.add(new WorkflowStepBranch.Branch(branchName, bodySteps));

                    return BranchEntry.this;
                }
            }
        }
    }

    /** Generic agent/code step entry — discriminates via {@code .agent(...)} / {@code .code(...)}. */
    public static final class StepEntry<P> {

        private final P parent;
        private final List<WorkflowStep> sink;

        private String stepName;

        StepEntry(P parent, List<WorkflowStep> sink) {

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
        private final List<WorkflowStep> sink;

        private final String stepName;
        private final String agent;

        private String instructions;
        private List<String> dependencies = List.of();
        private List<String> skills = List.of();
        private List<String> tools = List.of();
        private boolean hitl;

        AgentStepEntry(P parent, List<WorkflowStep> sink, String stepName, String agent) {

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

            sink.add(new WorkflowStepAgent(stepName, agent, instructions, dependencies, hitl, skills, tools));

            return parent;
        }
    }

    public static final class CodeStepEntry<P> {

        private final P parent;
        private final List<WorkflowStep> sink;

        private final String stepName;
        private final String slug;

        private Object input;
        private List<String> dependencies = List.of();

        CodeStepEntry(P parent, List<WorkflowStep> sink, String stepName, String slug) {

            this.parent = parent;
            this.sink = sink;
            this.stepName = stepName;
            this.slug = slug;
        }

        public <I> CodeStepEntry<P> input(I input)               { this.input = input; return this; }
        public CodeStepEntry<P> dependencies(String... deps)     { this.dependencies = List.of(deps); return this; }
        public CodeStepEntry<P> dependencies(List<String> deps)  { this.dependencies = deps; return this; }

        public P end() {

            sink.add(new WorkflowStepCode<>(stepName, slug, input, dependencies));

            return parent;
        }
    }
}
