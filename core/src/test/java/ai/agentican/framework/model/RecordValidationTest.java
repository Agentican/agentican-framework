package ai.agentican.framework.model;

import ai.agentican.framework.agent.Agent;
import ai.agentican.framework.agent.AgentRunner;
import ai.agentican.framework.orchestration.execution.WorkflowStepResult;
import ai.agentican.framework.orchestration.model.WorkflowDefinition;
import ai.agentican.framework.orchestration.execution.WorkflowRunStatus;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

import ai.agentican.framework.config.AgentConfig;
class RecordValidationTest {

    private final AgentRunner dummyRunner = (agent, task, taskId, stepId, stepName, timeout, skills, toolkits, outputSchema) -> null;

    @Test
    void agentRequiresName() {

        assertThrows(IllegalArgumentException.class, () -> Agent.builder().config(AgentConfig.builder().name(null).id(null).role("role").build()).runner(dummyRunner).build());
    }

    @Test
    void agentRequiresRole() {

        assertThrows(IllegalArgumentException.class, () -> Agent.builder().config(AgentConfig.builder().name("name").id("name").role(null).build()).runner(dummyRunner).build());
    }

    @Test
    void taskRequiresSteps() {

        assertThrows(IllegalArgumentException.class, () ->
                WorkflowDefinition.builder("name", "name").description("desc").build());
    }

    @Test
    void taskStepResultRequiresName() {

        assertThrows(IllegalArgumentException.class, () ->
                new WorkflowStepResult(null, WorkflowRunStatus.COMPLETED, "output", List.of()));
    }
}
