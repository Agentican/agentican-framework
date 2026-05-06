package ai.agentican.framework.orchestration.model;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

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

        public Builder param(String paramName) { params.add(new WorkflowParam(paramName, null, null, true)); return this; }
        public Builder param(String paramName, String description) { params.add(new WorkflowParam(paramName, description, null, true)); return this; }
        public Builder param(String paramName, String description, String defaultValue) { params.add(new WorkflowParam(paramName, description, defaultValue, false)); return this; }
        public Builder param(WorkflowParam param) { params.add(param); return this; }
        public Builder params(List<WorkflowParam> planParams) { this.params.addAll(planParams); return this; }

        public Builder step(WorkflowStep node) { steps.add(node); return this; }
        public Builder steps(List<WorkflowStep> bodySteps) { this.steps.addAll(bodySteps); return this; }

        public Builder step(String stepName, String agentName, String instructions) {

            steps.add(new WorkflowStepAgent(stepName, agentName, instructions, null, false, null, null));
            return this;
        }

        public Builder step(String stepName, String agentName, String instructions, boolean hitl) {

            steps.add(new WorkflowStepAgent(stepName, agentName, instructions, null, hitl, null, null));
            return this;
        }

        public Builder step(String stepName, String agentName, String instructions, List<String> dependencies) {

            steps.add(new WorkflowStepAgent(stepName, agentName, instructions, dependencies, false, null, null));
            return this;
        }

        public Builder loop(String stepName, Consumer<LoopBuilder> config) {

            var builder = new LoopBuilder(stepName);

            config.accept(builder);

            steps.add(builder.build());

            return this;
        }

        public Builder branch(String stepName, Consumer<BranchBuilder> config) {

            var builder = new BranchBuilder(stepName);

            config.accept(builder);

            steps.add(builder.build());

            return this;
        }

        public WorkflowDefinition build() {

            return new WorkflowDefinition(id, name, description, params, steps, outputStep);
        }
    }

    public static class LoopBuilder {

        private final String name;

        private final List<WorkflowStep> steps = new ArrayList<>();

        private boolean hitl;

        private String over;

        private List<String> dependencies = List.of();

        LoopBuilder(String name) {

            this.name = name;
        }

        public LoopBuilder over(String stepName) { this.over = stepName; return this; }
        public LoopBuilder step(WorkflowStep step) { this.steps.add(step); return this; }
        public LoopBuilder dependencies(List<String> dependencies) { this.dependencies = dependencies; return this; }
        public LoopBuilder hitl(boolean hitl) { this.hitl = hitl; return this; }

        WorkflowStepLoop build() {

            return new WorkflowStepLoop(name, over, steps, dependencies, hitl);
        }
    }

    public static class BranchBuilder {

        private final String name;

        private final List<WorkflowStepBranch.Path> paths = new ArrayList<>();

        private boolean hitl;

        private String from;
        private String defaultPath;

        private List<String> dependencies = List.of();

        BranchBuilder(String name) {

            this.name = name;
        }

        public BranchBuilder from(String stepName) { this.from = stepName; return this; }
        public BranchBuilder defaultPath(String pathName) { this.defaultPath = pathName; return this; }
        public BranchBuilder dependencies(List<String> dependencies) { this.dependencies = dependencies; return this; }
        public BranchBuilder hitl(boolean hitl) { this.hitl = hitl; return this; }

        public BranchBuilder path(String pathName, WorkflowStep... bodySteps) {
            paths.add(new WorkflowStepBranch.Path(pathName, List.of(bodySteps))); return this; }

        WorkflowStepBranch build() {

            return new WorkflowStepBranch(name, from, paths, defaultPath, dependencies, hitl);
        }
    }
}
