package ai.agentican.framework.model;

import ai.agentican.framework.agent.Agent;
import ai.agentican.framework.agent.AgentRunner;
import ai.agentican.framework.orchestration.execution.TaskStepResult;
import ai.agentican.framework.orchestration.model.Plan;
import ai.agentican.framework.orchestration.execution.TaskStatus;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

import ai.agentican.framework.config.AgentConfig;
class RecordValidationTest {

    private final AgentRunner dummyRunner = (agent, task, activeSkills, toolkits, taskId, stepId, stepName, timeout) -> null;

    @Test
    void agentRequiresName() {

        assertThrows(IllegalArgumentException.class, () -> Agent.builder().config(AgentConfig.builder().name(null).role("role").build()).runner(dummyRunner).build());
    }

    @Test
    void agentRequiresRole() {

        assertThrows(IllegalArgumentException.class, () -> Agent.builder().config(AgentConfig.builder().name("name").role(null).build()).runner(dummyRunner).build());
    }

    @Test
    void taskRequiresSteps() {

        assertThrows(IllegalArgumentException.class, () ->
                Plan.builder("name").description("desc").build());
    }

    @Test
    void taskStepResultRequiresName() {

        assertThrows(IllegalArgumentException.class, () ->
                new TaskStepResult(null, TaskStatus.COMPLETED, "output", List.of()));
    }
}
