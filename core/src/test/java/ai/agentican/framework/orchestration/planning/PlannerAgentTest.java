package ai.agentican.framework.orchestration.planning;

import ai.agentican.framework.MockLlmClient;
import ai.agentican.framework.MockToolkit;
import ai.agentican.framework.agent.Agent;
import ai.agentican.framework.agent.InMemoryAgentRegistry;
import ai.agentican.framework.agent.AgentResult;
import ai.agentican.framework.agent.AgentStatus;
import ai.agentican.framework.config.AgentConfig;
import ai.agentican.framework.orchestration.InMemoryPlanRegistry;
import ai.agentican.framework.orchestration.model.Plan;
import ai.agentican.framework.orchestration.model.PlanParam;
import ai.agentican.framework.orchestration.model.PlanStepAgent;
import ai.agentican.framework.skill.InMemorySkillRegistry;
import ai.agentican.framework.state.RunLog;
import ai.agentican.framework.tools.ToolDefinition;
import ai.agentican.framework.tools.ToolkitRegistry;
import ai.agentican.framework.util.Ids;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static ai.agentican.framework.MockLlmClient.*;
import static org.junit.jupiter.api.Assertions.*;

class PlannerAgentTest {

    private Function<AgentConfig, Agent> dummyAgentFactory() {

        return config -> new Agent(config, (agent, task, activeSkills, toolkits, taskId, stepId, stepName, timeout) ->
                        AgentResult.builder().status(AgentStatus.COMPLETED).run(new RunLog(Ids.generate(), 0, (String) null)).build());
    }

    @Test
    void planCreatesTaskAndRegistersAgents() {

        var planJson = """
                {
                  "type": "create",
                  "name": "Test Task",
                  "description": "A test",
                  "agents": [{"name": "test-agent", "role": "Tester"}],
                  "paramConfigs": [],
                  "stepConfigs": [
                    {"name": "step-a", "type": "agent", "agent": "test-agent", "instructions": "Do something", "toolkits": []}
                  ]
                }
                """;

        var mockLlm = new MockLlmClient()
                .onSend("planning-process", endTurn(planJson));

        var agentRegistry = new InMemoryAgentRegistry();
        var toolkitRegistry = new ToolkitRegistry();

        var planner = new PlannerAgent(mockLlm.toLlmClient(), agentRegistry, toolkitRegistry,
                new InMemorySkillRegistry(), new InMemoryPlanRegistry(), dummyAgentFactory());

        var result = planner.plan("Do a task");
        var task = result.plan();

        assertEquals("Test Task", task.name());
        assertEquals(1, task.steps().size());
        assertEquals("step-a", task.steps().getFirst().name());
        assertTrue(agentRegistry.isRegisteredByName("test-agent"));
        assertTrue(result.inputs().isEmpty(), "create path has no extracted inputs");
    }

    @Test
    void planRefinesStepsWithToolContext() {

        var planJson = """
                {
                  "type": "create",
                  "name": "Refined Task",
                  "description": "A test with tools",
                  "agents": [{"name": "tool-agent", "role": "Tool user"}],
                  "paramConfigs": [],
                  "stepConfigs": [
                    {"name": "tool-step", "type": "agent", "agent": "tool-agent", "instructions": "Use the tool", "tools": ["MY_TOOL"]}
                  ]
                }
                """;

        var refinedJson = """
                {
                  "paramConfigs": [],
                  "stepConfigs": [
                    {"name": "tool-step", "type": "agent", "agent": "tool-agent", "instructions": "Refined: use MY_TOOL with param q", "tools": ["MY_TOOL"]}
                  ]
                }
                """;

        var mockLlm = new MockLlmClient()
                .onSend("planning-process", endTurn(planJson))
                .onSend("plan refiner", endTurn(refinedJson));

        var agentRegistry = new InMemoryAgentRegistry();

        var toolkitRegistry = new ToolkitRegistry();
        var toolkit = new MockToolkit(List.of(
                new ToolDefinition("MY_TOOL", "A test tool", Map.of("q", Map.of("type", "string")))));
        toolkitRegistry.register("test-toolkit", toolkit);

        var planner = new PlannerAgent(mockLlm.toLlmClient(), agentRegistry, toolkitRegistry,
                new InMemorySkillRegistry(), new InMemoryPlanRegistry(), dummyAgentFactory());

        var task = planner.plan("Do a task with tools").plan();

        var step = (PlanStepAgent) task.steps().getFirst();
        assertEquals("Refined: use MY_TOOL with param q", step.instructions());
    }

    @Test
    void planSkipsRefinementForStepsWithoutTools() {

        var planJson = """
                {
                  "type": "create",
                  "name": "No-Tool Task",
                  "description": "A test without tools",
                  "agents": [{"name": "plain-agent", "role": "Worker"}],
                  "paramConfigs": [],
                  "stepConfigs": [
                    {"name": "plain-step", "type": "agent", "agent": "plain-agent", "instructions": "Just think", "tools": []}
                  ]
                }
                """;

        var mockLlm = new MockLlmClient()
                .onSend("planning-process", endTurn(planJson));

        var agentRegistry = new InMemoryAgentRegistry();
        var toolkitRegistry = new ToolkitRegistry();

        var planner = new PlannerAgent(mockLlm.toLlmClient(), agentRegistry, toolkitRegistry,
                new InMemorySkillRegistry(), new InMemoryPlanRegistry(), dummyAgentFactory());

        var task = planner.plan("Think about something").plan();

        var step = (PlanStepAgent) task.steps().getFirst();
        assertEquals("Just think", step.instructions());
    }

    @Test
    void planWithLoopStep() {

        var planJson = """
                {
                  "type": "create",
                  "name": "Loop Task",
                  "description": "A test with loop",
                  "agents": [{"name": "producer-agent", "role": "Producer"}, {"name": "body-agent", "role": "Processor"}],
                  "paramConfigs": [],
                  "stepConfigs": [
                    {"name": "produce", "type": "agent", "agent": "producer-agent", "instructions": "Produce items", "tools": ["MY_TOOL"]},
                    {"name": "process-loop", "type": "loop", "over": "produce", "stepConfigs": [
                      {"name": "process-item", "type": "agent", "agent": "body-agent", "instructions": "Process {{item}}", "tools": ["MY_TOOL"]}
                    ]}
                  ]
                }
                """;

        var refinedJson = """
                {
                  "paramConfigs": [],
                  "stepConfigs": [
                    {"name": "produce", "type": "agent", "agent": "producer-agent", "instructions": "Refined: produce items with MY_TOOL", "tools": ["MY_TOOL"]},
                    {"name": "process-loop", "type": "loop", "over": "produce", "stepConfigs": [
                      {"name": "process-item", "type": "agent", "agent": "body-agent", "instructions": "Refined: process {{item}} with MY_TOOL", "tools": ["MY_TOOL"]}
                    ]}
                  ]
                }
                """;

        var mockLlm = new MockLlmClient()
                .onSend("planning-process", endTurn(planJson))
                .onSend("plan refiner", endTurn(refinedJson));

        var agentRegistry = new InMemoryAgentRegistry();

        var toolkitRegistry = new ToolkitRegistry();
        var toolkit = new MockToolkit(List.of(
                new ToolDefinition("MY_TOOL", "A test tool", Map.of())));
        toolkitRegistry.register("test-toolkit", toolkit);

        var planner = new PlannerAgent(mockLlm.toLlmClient(), agentRegistry, toolkitRegistry,
                new InMemorySkillRegistry(), new InMemoryPlanRegistry(), dummyAgentFactory());

        var task = planner.plan("Produce and process items").plan();

        assertEquals("Loop Task", task.name());
        assertTrue(task.steps().size() >= 2);
        assertTrue(agentRegistry.isRegisteredByName("producer-agent"));
        assertTrue(agentRegistry.isRegisteredByName("body-agent"));
    }

    @Test
    void planReusesExistingPlanWhenLlmReturnsReuseDecision() {

        var existing = Plan.builder("Research Plan")
                .id("plan-cataloged-id")
                .description("Research any topic")
                .param(new PlanParam("topic", null, null, true))
                .step(new PlanStepAgent("research", "researcher", "research {{param.topic}}",
                        List.of(), false, List.of(), List.of()))
                .build();

        var planRegistry = new InMemoryPlanRegistry();
        planRegistry.register(existing);

        var reuseJson = """
                {
                  "type": "reuse",
                  "planRef": "plan-cataloged-id",
                  "inputs": {"topic": "quantum computing"}
                }
                """;

        var mockLlm = new MockLlmClient()
                .onSend("planning-process", endTurn(reuseJson));

        var planner = new PlannerAgent(mockLlm.toLlmClient(), new InMemoryAgentRegistry(),
                new ToolkitRegistry(), new InMemorySkillRegistry(), planRegistry, dummyAgentFactory());

        var result = planner.plan("Research quantum computing");

        assertSame(existing, result.plan(), "Reused plan should be the one returned from the catalog");
        assertEquals(Map.of("topic", "quantum computing"), result.inputs());
    }

    @Test
    void planFallsBackToCreateWhenReuseRefIsUnknown() {

        var planRegistry = new InMemoryPlanRegistry();

        var hallucinatedReuse = """
                { "type": "reuse", "planRef": "does-not-exist", "inputs": {} }
                """;

        var fallbackCreate = """
                {
                  "type": "create",
                  "name": "Fallback Task",
                  "description": "created after reuse miss",
                  "agents": [{"name": "fallback-agent", "role": "Worker"}],
                  "paramConfigs": [],
                  "stepConfigs": [
                    {"name": "fallback-step", "type": "agent", "agent": "fallback-agent", "instructions": "do it"}
                  ]
                }
                """;

        var mockLlm = new MockLlmClient()
                .onSend("planning-process", endTurn(hallucinatedReuse))
                .onSend("planning-process", endTurn(fallbackCreate));

        var planner = new PlannerAgent(mockLlm.toLlmClient(), new InMemoryAgentRegistry(),
                new ToolkitRegistry(), new InMemorySkillRegistry(), planRegistry, dummyAgentFactory());

        var result = planner.plan("novel task");

        assertEquals("Fallback Task", result.plan().name());
        assertTrue(result.inputs().isEmpty());
    }
}
