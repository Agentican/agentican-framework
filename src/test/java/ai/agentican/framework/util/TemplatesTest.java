package ai.agentican.framework.util;

import ai.agentican.framework.config.SkillConfig;
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

        var skills = List.of(SkillConfig.of("summarize", "Summarize long text"));

        var result = templates.renderSystemPrompt("TestBot", "A helper", skills);

        assertTrue(result.contains("summarize"), "Expected skill name in: " + result);
        assertTrue(result.contains("Summarize long text"), "Expected skill instructions in: " + result);
    }

    @Test
    void renderTaskBlock() {

        var result = templates.renderTaskBlock("Write a poem about cats");

        assertTrue(result.contains("<task>"), "Expected task tag in: " + result);
        assertTrue(result.contains("Write a poem about cats"), "Expected task content in: " + result);
    }

    @Test
    void renderUserMessageWithProgress() {

        var progress = List.of(
                new ai.agentican.framework.agent.ProgressEntry("web_search", "{\"q\":\"foo\"}", "{\"results\":[]}"));

        var result = templates.renderUserMessage(1, List.of(), List.of(), progress, List.of(), List.of());

        assertTrue(result.contains("<progress>"), "Expected progress block in: " + result);
        assertTrue(result.contains("web_search"), "Expected tool name in progress: " + result);
    }

    @Test
    void renderUserMessageOmitsTask() {

        var result = templates.renderUserMessage(0, List.of(), List.of(), List.of(), List.of(), List.of());

        assertFalse(result.contains("<task>"), "Task block must not be rendered in user message: " + result);
    }

    @Test
    void renderPlannerPromptWithNoAgentsOrTools() {

        var result = templates.renderPlannerPrompt(List.of(), List.of(), List.of(), List.of());

        assertTrue(result.contains("No agents configured"), "Expected no-agents indicator in: " + result);
        assertTrue(result.contains("No tools connected"), "Expected no-tools indicator in: " + result);
    }

    @Test
    void renderPlannerPromptContainsPlanningProcess() {

        var result = templates.renderPlannerPrompt(List.of(), List.of(), List.of(), List.of());

        assertTrue(result.contains("planning-process"), "Expected planning-process section in: " + result);
    }

    @Test
    void renderRefinePlanMessage() {

        var planJson = "{\"steps\":[{\"name\":\"my-step\"}]}";

        var result = templates.renderRefinePlanMessage(planJson, List.of(), List.of(), List.of());

        assertTrue(result.contains("my-step"), "Expected plan JSON to be rendered in: " + result);
        assertTrue(result.contains("initial-plan"), "Expected <initial-plan> tag in: " + result);
    }
}
