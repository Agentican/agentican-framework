package ai.agentican.quarkus.test;

import ai.agentican.framework.orchestration.model.Plan;
import ai.agentican.framework.orchestration.model.PlanStepAgent;

import java.util.List;

public final class TestTaskBuilder {

    private TestTaskBuilder() {}

    public static Plan singleStep(String taskName, String agentName, String instructions) {

        var step = new PlanStepAgent("step", agentName, instructions,
                List.of(), false, List.of(), List.of());

        return Plan.builder(taskName).description("test task").step(step).build();
    }

    public static Plan twoStepSequential(String taskName, String agentName,
                                          String instructions1, String instructions2) {

        var step1 = new PlanStepAgent("step1", agentName, instructions1,
                List.of(), false, List.of(), List.of());
        var step2 = new PlanStepAgent("step2", agentName, instructions2,
                List.of("step1"), false, List.of(), List.of());

        return Plan.builder(taskName).description("test task").steps(List.of(step1, step2)).build();
    }
}
