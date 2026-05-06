package ai.agentican.framework.orchestration.model;

import java.util.ArrayList;
import java.util.List;

public record WorkflowStepBranch(
        String name,
        String from,
        List<Path> paths,
        String defaultPath,
        List<String> dependencies,
        boolean hitl) implements WorkflowStep {

    public WorkflowStepBranch {

        if (name == null || name.isBlank())
            throw new IllegalArgumentException("Branch step name is required");

        if (from == null || from.isBlank())
            throw new IllegalArgumentException("'from' step name is required for branch step '" + name + "'");

        if (paths == null || paths.isEmpty())
            throw new IllegalArgumentException("At least one path is required for branch step '" + name + "'");

        paths = List.copyOf(paths);

        if (dependencies == null)
            dependencies = List.of();
    }

    public static Builder builder(String name) {

        return new Builder(name);
    }

    public static class Builder {

        private final String name;
        private final List<Path> paths = new ArrayList<>();
        private final List<String> dependencies = new ArrayList<>();

        private String from;
        private String defaultPath;
        private boolean hitl;

        Builder(String name) {

            this.name = name;
        }

        public Builder from(String stepName) { this.from = stepName; return this; }
        public Builder defaultPath(String pathName) { this.defaultPath = pathName; return this; }
        public Builder hitl(boolean hitl) { this.hitl = hitl; return this; }
        public Builder dependency(String stepName) { this.dependencies.add(stepName); return this; }
        public Builder dependencies(List<String> stepNames) { this.dependencies.addAll(stepNames); return this; }
        public Builder path(String pathName, WorkflowStep... body) { this.paths.add(new Path(pathName, List.of(body))); return this; }
        public Builder path(String pathName, List<WorkflowStep> body) { this.paths.add(new Path(pathName, body)); return this; }

        public WorkflowStepBranch build() {

            return new WorkflowStepBranch(name, from, paths, defaultPath, dependencies, hitl);
        }
    }

    public record Path(
            String pathName,
            List<WorkflowStep> body) {

        public Path {

            if (pathName == null || pathName.isBlank())
                throw new IllegalArgumentException("Path name is required");

            if (body == null || body.isEmpty())
                throw new IllegalArgumentException("Body is required for path '" + pathName + "'");

            body = List.copyOf(body);
        }
    }
}
