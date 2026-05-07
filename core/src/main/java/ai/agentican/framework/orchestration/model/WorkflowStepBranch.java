package ai.agentican.framework.orchestration.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

public record WorkflowStepBranch(
        String name,
        String from,
        List<Branch> branches,
        @JsonProperty("default") String defaultBranch,
        List<String> dependencies,
        boolean hitl) implements WorkflowStep {

    public WorkflowStepBranch {

        if (name == null || name.isBlank())
            throw new IllegalArgumentException("Branch step name is required");

        if (from == null || from.isBlank())
            throw new IllegalArgumentException("'from' step name is required for branch step '" + name + "'");

        if (branches == null || branches.isEmpty())
            throw new IllegalArgumentException("At least one branch is required for branch step '" + name + "'");

        branches = List.copyOf(branches);

        if (dependencies == null)
            dependencies = List.of();
    }

    public static Builder builder(String name) {

        return new Builder(name);
    }

    public static class Builder {

        private final String name;
        private final List<Branch> branches = new ArrayList<>();
        private final List<String> dependencies = new ArrayList<>();

        private String from;
        private String defaultBranch;
        private boolean hitl;

        Builder(String name) {

            this.name = name;
        }

        public Builder from(String stepName) { this.from = stepName; return this; }
        public Builder defaultBranch(String branchName) { this.defaultBranch = branchName; return this; }
        public Builder hitl(boolean hitl) { this.hitl = hitl; return this; }
        public Builder dependency(String stepName) { this.dependencies.add(stepName); return this; }
        public Builder dependencies(List<String> stepNames) { this.dependencies.addAll(stepNames); return this; }
        public Builder branch(String branchName, WorkflowStep... steps) { this.branches.add(new Branch(branchName, List.of(steps))); return this; }
        public Builder branch(String branchName, List<WorkflowStep> steps) { this.branches.add(new Branch(branchName, steps)); return this; }

        public WorkflowStepBranch build() {

            return new WorkflowStepBranch(name, from, branches, defaultBranch, dependencies, hitl);
        }
    }

    public record Branch(
            String name,
            List<WorkflowStep> steps) {

        public Branch {

            if (name == null || name.isBlank())
                throw new IllegalArgumentException("Branch name is required");

            if (steps == null || steps.isEmpty())
                throw new IllegalArgumentException("Steps are required for branch '" + name + "'");

            steps = List.copyOf(steps);
        }
    }
}
