package ai.agentican.quarkus;

import ai.agentican.framework.invoker.AgenticanTask;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@QuarkusTest
class AgenticanTaskQualifierTest {

    static final String AGENT_NAME   = "researcher";
    static final String TASK_NAME    = "Research Question";
    static final String INSTRUCTIONS = "Research {{input}} and summarize the findings.";

    @Inject
    @AgentTask(name = TASK_NAME, agent = AGENT_NAME, instructions = INSTRUCTIONS)
    AgenticanTask<String, String> researcher;

    @Inject
    @AgentTask(
            name = "Research With Skill",
            agent = AGENT_NAME,
            instructions = INSTRUCTIONS,
            skills = {"literature-search"},
            hitl = true)
    AgenticanTask<String, String> researcherWithOpts;

    @Inject
    @WorkflowTask(name = "Unresolved Plan", plan = "not-yet-registered")
    AgenticanTask<String, String> unresolved;

    @Test
    void agentTaskQualifierResolves() {

        assertNotNull(researcher);
    }

    @Test
    void agentTaskQualifierAcceptsSkillsAndHitl() {

        assertNotNull(researcherWithOpts);
    }

    @Test
    void workflowTaskQualifierResolvesEvenWithoutRegisteredPlan() {

        assertNotNull(unresolved);
    }
}
