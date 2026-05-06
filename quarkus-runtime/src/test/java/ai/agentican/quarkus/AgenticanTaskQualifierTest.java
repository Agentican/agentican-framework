package ai.agentican.quarkus;

import ai.agentican.framework.Workflow;

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
    @Task(name = TASK_NAME, agent = AGENT_NAME, instructions = INSTRUCTIONS)
    Workflow<String, String> researcher;

    @Inject
    @Task(
            name = "Research With Skill",
            agent = AGENT_NAME,
            instructions = INSTRUCTIONS,
            skills = {"literature-search"},
            hitl = true)
    Workflow<String, String> researcherWithOpts;

    @Test
    void agentTaskQualifierResolves() {

        assertNotNull(researcher);
    }

    @Test
    void agentTaskQualifierAcceptsSkillsAndHitl() {

        assertNotNull(researcherWithOpts);
    }
}
