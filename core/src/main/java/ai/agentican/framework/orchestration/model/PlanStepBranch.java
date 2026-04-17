package ai.agentican.framework.orchestration.model;

import java.util.List;

public record PlanStepBranch(
        String name,
        String from,
        List<Path> paths,
        String defaultPath,
        List<String> dependencies,
        boolean hitl) implements PlanStep {

    public PlanStepBranch {

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

    public static PlanStepBranch of(String name, String from, List<Path> paths, String defaultPath,
                                    List<String> dependencies, boolean hitl) {

        return new PlanStepBranch(name, from, paths, defaultPath, dependencies, hitl);
    }

    public record Path(
            String pathName,
            List<PlanStep> body) {

        public Path {

            if (pathName == null || pathName.isBlank())
                throw new IllegalArgumentException("Path name is required");

            if (body == null || body.isEmpty())
                throw new IllegalArgumentException("Body is required for path '" + pathName + "'");

            body = List.copyOf(body);
        }

        public static Path of(String pathName, List<PlanStep> body) {

            return new Path(pathName, body);
        }
    }
}
