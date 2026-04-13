package ai.agentican.framework.util;

import ai.agentican.framework.config.SkillConfig;
import ai.agentican.framework.orchestration.model.PlanStepAgent;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TemplatesTest {

    private final Templates templates = new Templates();

    @Test
    void renderSystemPrompt() {

        var result = templates.renderSystemPrompt("TestBot", "A helpful assistant");

        assertTrue(result.contains("TestBot"), "Expected agent name in: " + result);
        assertTrue(result.contains("A helpful assistant"), "Expected role in: " + result);
    }

    @Test
    void renderSystemPromptWithSkills() {

        var skills = List.of(new SkillConfig("summarize", "Summarize long text"));

        var result = templates.renderSystemPrompt("TestBot", "A helper", skills);

        assertTrue(result.contains("summarize"), "Expected skill name in: " + result);
        assertTrue(result.contains("Summarize long text"), "Expected skill instructions in: " + result);
    }

    @Test
    void renderUserMessage() {

        var result = templates.renderUserMessage("Write a poem about cats", 0, List.of(), List.of());

        assertTrue(result.contains("Write a poem about cats"), "Expected task in: " + result);
        assertTrue(result.contains("Use your available tools"), "Expected iteration 0 text in: " + result);
    }

    @Test
    void renderUserMessageSecondIteration() {

        var result = templates.renderUserMessage("Do something", 1, List.of(), List.of());

        assertTrue(result.contains("Review the tool results"), "Expected iteration > 0 text in: " + result);
    }

    @Test
    void renderPlannerPromptWithNoAgentsOrToolkits() {

        var result = templates.renderPlannerPrompt(List.of(), List.of());

        assertTrue(result.contains("No agents configured"), "Expected no-agents indicator in: " + result);
        assertTrue(result.contains("No toolkits connected"), "Expected no-toolkits indicator in: " + result);
    }

    @Test
    void renderPlannerPromptContainsPlanningProcess() {

        var result = templates.renderPlannerPrompt(List.of(), List.of());

        assertTrue(result.contains("planning-process"), "Expected planning-process section in: " + result);
    }

    @Test
    void renderRefineAgentStepMessage() {

        var step = new PlanStepAgent("my-step", "my-agent", "Do the thing", List.of(), false, List.of(), List.of());

        var result = templates.renderRefineAgentStepMessage(step, "Expert analyst", List.of());

        assertTrue(result.contains("my-step"), "Expected step name in: " + result);
        assertTrue(result.contains("Expert analyst"), "Expected agent role in: " + result);
        assertTrue(result.contains("Do the thing"), "Expected instructions in: " + result);
    }
}
