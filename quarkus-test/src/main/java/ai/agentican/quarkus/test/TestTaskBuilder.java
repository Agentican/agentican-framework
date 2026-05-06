package ai.agentican.quarkus.test;

import ai.agentican.framework.orchestration.model.WorkflowDefinition;
import ai.agentican.framework.orchestration.model.WorkflowStepAgent;

import java.util.List;

public final class TestTaskBuilder {

    private TestTaskBuilder() {}

    public static WorkflowDefinition singleStep(String taskName, String agentName, String instructions) {

        var step = new WorkflowStepAgent("step", agentName, instructions,
                List.of(), false, List.of(), List.of());

        return WorkflowDefinition.builder(taskName, taskName).description("test task").step(step).build();
    }

    public static WorkflowDefinition twoStepSequential(String taskName, String agentName,
                                          String instructions1, String instructions2) {

        var step1 = new WorkflowStepAgent("step1", agentName, instructions1,
                List.of(), false, List.of(), List.of());
        var step2 = new WorkflowStepAgent("step2", agentName, instructions2,
                List.of("step1"), false, List.of(), List.of());

        return WorkflowDefinition.builder(taskName, taskName).description("test task").steps(List.of(step1, step2)).build();
    }
}
