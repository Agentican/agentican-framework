package ai.agentican.framework.orchestration.model;

import java.util.ArrayList;
import java.util.List;

public record WorkflowStepLoop(
        String name,
        String over,
        List<WorkflowStep> body,
        List<String> dependencies,
        boolean hitl) implements WorkflowStep {

    public WorkflowStepLoop {

        if (name == null || name.isBlank())
            throw new IllegalArgumentException("Loop step name is required");

        if (over == null || over.isBlank())
            throw new IllegalArgumentException("'over' step name is required for loop step '" + name + "'");

        if (body == null || body.isEmpty())
            throw new IllegalArgumentException("Loop body is required for loop step '" + name + "'");

        body = List.copyOf(body);

        if (dependencies == null)
            dependencies = List.of();
    }

    public static Builder builder(String name) {

        return new Builder(name);
    }

    public static class Builder {

        private final String name;
        private final List<WorkflowStep> body = new ArrayList<>();
        private final List<String> dependencies = new ArrayList<>();

        private String over;
        private boolean hitl;

        Builder(String name) {

            this.name = name;
        }

        public Builder over(String stepName) { this.over = stepName; return this; }
        public Builder hitl(boolean hitl) { this.hitl = hitl; return this; }
        public Builder step(WorkflowStep step) { this.body.add(step); return this; }
        public Builder steps(WorkflowStep... steps) { this.body.addAll(List.of(steps)); return this; }
        public Builder steps(List<WorkflowStep> steps) { this.body.addAll(steps); return this; }
        public Builder dependency(String stepName) { this.dependencies.add(stepName); return this; }
        public Builder dependencies(List<String> stepNames) { this.dependencies.addAll(stepNames); return this; }

        public WorkflowStepLoop build() {

            return new WorkflowStepLoop(name, over, body, dependencies, hitl);
        }
    }
}
