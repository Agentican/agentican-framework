package ai.agentican.framework.orchestration.model;

import ai.agentican.framework.util.Ids;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public record Plan(
        String id,
        String name,
        String description,
        List<PlanParam> params,
        List<PlanStep> steps,
        String externalId,
        String outputStep) {

    public Plan {

        if (id == null)
            id = Ids.generate();

        if (name == null || name.isBlank())
            throw new IllegalArgumentException("Plan name is required");

        if (steps == null || steps.isEmpty())
            throw new IllegalArgumentException("Plan must have at least one node");

        if (params == null)
            params = List.of();

        params = List.copyOf(params);
        steps = List.copyOf(steps);

        if (externalId != null && externalId.isBlank())
            externalId = null;

        if (outputStep != null && outputStep.isBlank())
            outputStep = null;
    }

    public static PlanBuilder builder(String name) {

        return new PlanBuilder(name);
    }

    public static class PlanBuilder {

        private final String name;

        private final List<PlanParam> params = new ArrayList<>();
        private final List<PlanStep> steps = new ArrayList<>();

        private String id;
        private String description;
        private String externalId;
        private String outputStep;

        PlanBuilder(String name) {

            this.name = name;
        }

        public PlanBuilder id(String id) { this.id = id; return this; }
        public PlanBuilder description(String description) { this.description = description; return this; }
        public PlanBuilder externalId(String externalId) { this.externalId = externalId; return this; }
        public PlanBuilder outputStep(String stepName) { this.outputStep = stepName; return this; }

        public PlanBuilder param(String paramName) { params.add(new PlanParam(paramName, null, null, true)); return this; }
        public PlanBuilder param(String paramName, String description) { params.add(new PlanParam(paramName, description, null, true)); return this; }
        public PlanBuilder param(String paramName, String description, String defaultValue) { params.add(new PlanParam(paramName, description, defaultValue, false)); return this; }
        public PlanBuilder param(PlanParam param) { params.add(param); return this; }
        public PlanBuilder params(List<PlanParam> planParams) { this.params.addAll(planParams); return this; }

        public PlanBuilder step(PlanStep node) { steps.add(node); return this; }
        public PlanBuilder steps(List<PlanStep> bodySteps) { this.steps.addAll(bodySteps); return this; }

        public PlanBuilder step(String stepName, String agentName, String instructions) {

            steps.add(new PlanStepAgent(stepName, agentName, instructions, null, false, null, null));
            return this;
        }

        public PlanBuilder step(String stepName, String agentName, String instructions, boolean hitl) {

            steps.add(new PlanStepAgent(stepName, agentName, instructions, null, hitl, null, null));
            return this;
        }

        public PlanBuilder step(String stepName, String agentName, String instructions, List<String> dependencies) {

            steps.add(new PlanStepAgent(stepName, agentName, instructions, dependencies, false, null, null));
            return this;
        }

        public PlanBuilder loop(String stepName, Consumer<LoopStepBuilder> config) {

            var builder = new LoopStepBuilder(stepName);

            config.accept(builder);

            steps.add(builder.build());

            return this;
        }

        public PlanBuilder branch(String stepName, Consumer<BranchStepBuilder> config) {

            var builder = new BranchStepBuilder(stepName);

            config.accept(builder);

            steps.add(builder.build());

            return this;
        }

        public Plan build() {

            return new Plan(id, name, description, params, steps, externalId, outputStep);
        }
    }

    public static class LoopStepBuilder {

        private final String name;

        private final List<PlanStep> steps = new ArrayList<>();

        private boolean hitl;

        private String over;

        private List<String> dependencies = List.of();

        LoopStepBuilder(String name) {

            this.name = name;
        }

        public LoopStepBuilder over(String stepName) { this.over = stepName; return this; }
        public LoopStepBuilder step(PlanStep step) { this.steps.add(step); return this; }
        public LoopStepBuilder dependencies(List<String> dependencies) { this.dependencies = dependencies; return this; }
        public LoopStepBuilder hitl(boolean hitl) { this.hitl = hitl; return this; }

        PlanStepLoop build() {

            return new PlanStepLoop(name, over, steps, dependencies, hitl);
        }
    }

    public static class BranchStepBuilder {

        private final String name;

        private final List<PlanStepBranch.Path> paths = new ArrayList<>();

        private boolean hitl;

        private String from;
        private String defaultPath;

        private List<String> dependencies = List.of();

        BranchStepBuilder(String name) {

            this.name = name;
        }

        public BranchStepBuilder from(String stepName) { this.from = stepName; return this; }
        public BranchStepBuilder defaultPath(String pathName) { this.defaultPath = pathName; return this; }
        public BranchStepBuilder dependencies(List<String> dependencies) { this.dependencies = dependencies; return this; }
        public BranchStepBuilder hitl(boolean hitl) { this.hitl = hitl; return this; }

        public BranchStepBuilder path(String pathName, PlanStep... bodySteps) {
            paths.add(new PlanStepBranch.Path(pathName, List.of(bodySteps))); return this; }

        PlanStepBranch build() {

            return new PlanStepBranch(name, from, paths, defaultPath, dependencies, hitl);
        }
    }
}
