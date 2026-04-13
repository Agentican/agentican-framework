package ai.agentican.framework.model;

import ai.agentican.framework.agent.Agent;
import ai.agentican.framework.agent.AgentRunner;
import ai.agentican.framework.orchestration.execution.TaskStepResult;
import ai.agentican.framework.orchestration.model.Plan;
import ai.agentican.framework.orchestration.execution.TaskStatus;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RecordValidationTest {

    private final AgentRunner dummyRunner = (agent, task, activeSkills, toolkits, taskId, stepId, stepName) -> null;

    @Test
    void agentRequiresName() {

        assertThrows(IllegalArgumentException.class, () -> new Agent(null, "role", List.of(), dummyRunner));
    }

    @Test
    void agentRequiresRole() {

        assertThrows(IllegalArgumentException.class, () -> new Agent("name", null, List.of(), dummyRunner));
    }

    @Test
    void taskRequiresSteps() {

        assertThrows(IllegalArgumentException.class, () ->
                new Plan(null, "name", "desc", List.of(), List.of()));
    }

    @Test
    void taskStepResultRequiresName() {

        assertThrows(IllegalArgumentException.class, () ->
                new TaskStepResult(null, TaskStatus.COMPLETED, "output", List.of()));
    }
}
