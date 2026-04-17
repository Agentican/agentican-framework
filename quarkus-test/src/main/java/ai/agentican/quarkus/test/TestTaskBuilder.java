package ai.agentican.quarkus.test;

import ai.agentican.framework.orchestration.model.Plan;
import ai.agentican.framework.orchestration.model.PlanStepAgent;

import java.util.List;

public final class TestTaskBuilder {

    private TestTaskBuilder() {}

    public static Plan singleStep(String taskName, String agentName, String instructions) {

        var step = PlanStepAgent.of("step", agentName, instructions,
                List.of(), false, List.of(), List.of());

        return Plan.of(taskName, "test task", List.of(), List.of(step));
    }

    public static Plan twoStepSequential(String taskName, String agentName,
                                          String instructions1, String instructions2) {

        var step1 = PlanStepAgent.of("step1", agentName, instructions1,
                List.of(), false, List.of(), List.of());
        var step2 = PlanStepAgent.of("step2", agentName, instructions2,
                List.of("step1"), false, List.of(), List.of());

        return Plan.of(taskName, "test task", List.of(), List.of(step1, step2));
    }
}
