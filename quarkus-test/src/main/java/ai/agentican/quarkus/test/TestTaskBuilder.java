package ai.agentican.quarkus.test;

import ai.agentican.framework.orchestration.model.WorkflowDefinition;

public final class TestTaskBuilder {

    private TestTaskBuilder() {}

    public static WorkflowDefinition singleStep(String taskName, String agentName, String instructions) {

        return WorkflowDefinition.builder(taskName, taskName).description("test task")
                .step().name("step").agent(agentName).instructions(instructions).end()
                .build();
    }

    public static WorkflowDefinition twoStepSequential(String taskName, String agentName,
                                          String instructions1, String instructions2) {

        return WorkflowDefinition.builder(taskName, taskName).description("test task")
                .step().name("step1").agent(agentName).instructions(instructions1).end()
                .step().name("step2").agent(agentName).instructions(instructions2)
                        .dependencies("step1").end()
                .build();
    }
}
