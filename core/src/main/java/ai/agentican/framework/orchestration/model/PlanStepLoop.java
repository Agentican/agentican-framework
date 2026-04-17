package ai.agentican.framework.orchestration.model;

import java.util.List;

public record PlanStepLoop(
        String name,
        String over,
        List<PlanStep> body,
        List<String> dependencies,
        boolean hitl) implements PlanStep {

    public PlanStepLoop {

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
}
