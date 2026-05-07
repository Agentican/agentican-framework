package ai.agentican.framework.orchestration.planning;

import ai.agentican.framework.MockLlmClient;
import ai.agentican.framework.MockToolkit;
import ai.agentican.framework.agent.Agent;
import ai.agentican.framework.registry.AgentRegistryMemory;
import ai.agentican.framework.agent.AgentResult;
import ai.agentican.framework.agent.AgentStatus;
import ai.agentican.framework.config.AgentConfig;
import ai.agentican.framework.registry.WorkflowRegistryMemory;
import ai.agentican.framework.orchestration.model.WorkflowDefinition;
import ai.agentican.framework.orchestration.model.WorkflowParam;
import ai.agentican.framework.orchestration.model.WorkflowStepAgent;
import ai.agentican.framework.registry.SkillRegistryMemory;
import ai.agentican.framework.state.RunLog;
import ai.agentican.framework.tools.ToolDefinition;
import ai.agentican.framework.registry.ToolkitRegistry;
import ai.agentican.framework.util.Ids;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static ai.agentican.framework.MockLlmClient.*;
import static org.junit.jupiter.api.Assertions.*;

class PlannerAgentTest {

    private Function<AgentConfig, Agent> dummyAgentFactory() {

        return config -> new Agent(config, (agent, task, taskId, stepId, stepName, timeout, skills, toolkits, outputSchema) ->
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
                  "params": [],
                  "steps": [
                    {"name": "step-a", "type": "agent", "agent": "test-agent", "instructions": "Do something", "toolkits": []}
                  ]
                }
                """;

        var mockLlm = new MockLlmClient()
                .onSend("planning-process", endTurn(planJson));

        var agentRegistry = new AgentRegistryMemory();
        var toolkitRegistry = new ToolkitRegistry();

        var planner = new WorkflowPlannerAgent(mockLlm.toLlmClient(), agentRegistry, toolkitRegistry,
                new SkillRegistryMemory(), new WorkflowRegistryMemory(), dummyAgentFactory(), false);

        var result = planner.plan("Do a task");
        var task = result.definition();

        assertEquals("Test Task", task.name());
        assertEquals(1, task.steps().size());
        assertEquals("step-a", task.steps().getFirst().name());
        assertTrue(agentRegistry.hasByName("test-agent"));
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
                  "params": [],
                  "steps": [
                    {"name": "tool-step", "type": "agent", "agent": "tool-agent", "instructions": "Use the tool", "tools": ["MY_TOOL"]}
                  ]
                }
                """;

        var refinedJson = """
                {
                  "params": [],
                  "steps": [
                    {"name": "tool-step", "type": "agent", "agent": "tool-agent", "instructions": "Refined: use MY_TOOL with param q", "tools": ["MY_TOOL"]}
                  ]
                }
                """;

        var mockLlm = new MockLlmClient()
                .onSend("planning-process", endTurn(planJson))
                .onSend("definition refiner", endTurn(refinedJson));

        var agentRegistry = new AgentRegistryMemory();

        var toolkitRegistry = new ToolkitRegistry();
        var toolkit = new MockToolkit(List.of(
                new ToolDefinition("MY_TOOL", "A test tool", Map.of("q", Map.of("type", "string")))));
        toolkitRegistry.register("test-toolkit", toolkit);

        var planner = new WorkflowPlannerAgent(mockLlm.toLlmClient(), agentRegistry, toolkitRegistry,
                new SkillRegistryMemory(), new WorkflowRegistryMemory(), dummyAgentFactory(), false);

        var task = planner.plan("Do a task with tools").definition();

        var step = (WorkflowStepAgent) task.steps().getFirst();
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
                  "params": [],
                  "steps": [
                    {"name": "plain-step", "type": "agent", "agent": "plain-agent", "instructions": "Just think", "tools": []}
                  ]
                }
                """;

        var mockLlm = new MockLlmClient()
                .onSend("planning-process", endTurn(planJson));

        var agentRegistry = new AgentRegistryMemory();
        var toolkitRegistry = new ToolkitRegistry();

        var planner = new WorkflowPlannerAgent(mockLlm.toLlmClient(), agentRegistry, toolkitRegistry,
                new SkillRegistryMemory(), new WorkflowRegistryMemory(), dummyAgentFactory(), false);

        var task = planner.plan("Think about something").definition();

        var step = (WorkflowStepAgent) task.steps().getFirst();
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
                  "params": [],
                  "steps": [
                    {"name": "produce", "type": "agent", "agent": "producer-agent", "instructions": "Produce items", "tools": ["MY_TOOL"]},
                    {"name": "process-loop", "type": "loop", "over": "produce", "steps": [
                      {"name": "process-item", "type": "agent", "agent": "body-agent", "instructions": "Process {{item}}", "tools": ["MY_TOOL"]}
                    ]}
                  ]
                }
                """;

        var refinedJson = """
                {
                  "params": [],
                  "steps": [
                    {"name": "produce", "type": "agent", "agent": "producer-agent", "instructions": "Refined: produce items with MY_TOOL", "tools": ["MY_TOOL"]},
                    {"name": "process-loop", "type": "loop", "over": "produce", "steps": [
                      {"name": "process-item", "type": "agent", "agent": "body-agent", "instructions": "Refined: process {{item}} with MY_TOOL", "tools": ["MY_TOOL"]}
                    ]}
                  ]
                }
                """;

        var mockLlm = new MockLlmClient()
                .onSend("planning-process", endTurn(planJson))
                .onSend("definition refiner", endTurn(refinedJson));

        var agentRegistry = new AgentRegistryMemory();

        var toolkitRegistry = new ToolkitRegistry();
        var toolkit = new MockToolkit(List.of(
                new ToolDefinition("MY_TOOL", "A test tool", Map.of())));
        toolkitRegistry.register("test-toolkit", toolkit);

        var planner = new WorkflowPlannerAgent(mockLlm.toLlmClient(), agentRegistry, toolkitRegistry,
                new SkillRegistryMemory(), new WorkflowRegistryMemory(), dummyAgentFactory(), false);

        var task = planner.plan("Produce and process items").definition();

        assertEquals("Loop Task", task.name());
        assertTrue(task.steps().size() >= 2);
        assertTrue(agentRegistry.hasByName("producer-agent"));
        assertTrue(agentRegistry.hasByName("body-agent"));
    }

    @Test
    void planReusesExistingPlanWhenLlmReturnsReuseDecision() {

        var existing = WorkflowDefinition.builder("definition-cataloged-id", "Research WorkflowDefinition")
                .description("Research any topic")
                .param().name("topic").required(true).end()
                .step().name("research").agent("researcher").instructions("research {{param.topic}}").end()
                .build();

        var workflowRegistry = new WorkflowRegistryMemory();
        workflowRegistry.register(existing);

        var reuseJson = """
                {
                  "type": "reuse",
                  "name": "Research WorkflowDefinition",
                  "inputs": {"topic": "quantum computing"}
                }
                """;

        var mockLlm = new MockLlmClient()
                .onSend("planning-process", endTurn(reuseJson));

        var planner = new WorkflowPlannerAgent(mockLlm.toLlmClient(), new AgentRegistryMemory(),
                new ToolkitRegistry(), new SkillRegistryMemory(), workflowRegistry, dummyAgentFactory(), false);

        var result = planner.plan("Research quantum computing");

        assertSame(existing, result.definition(), "Reused definition should be the one returned from the catalog");
        assertEquals(Map.of("topic", "quantum computing"), result.inputs());
    }

    @Test
    void planFallsBackToCreateWhenReuseRefIsUnknown() {

        var workflowRegistry = new WorkflowRegistryMemory();

        var hallucinatedReuse = """
                { "type": "reuse", "name": "does-not-exist", "inputs": {} }
                """;

        var fallbackCreate = """
                {
                  "type": "create",
                  "name": "Fallback Task",
                  "description": "created after reuse miss",
                  "agents": [{"name": "fallback-agent", "role": "Worker"}],
                  "params": [],
                  "steps": [
                    {"name": "fallback-step", "type": "agent", "agent": "fallback-agent", "instructions": "do it"}
                  ]
                }
                """;

        var mockLlm = new MockLlmClient()
                .onSend("planning-process", endTurn(hallucinatedReuse))
                .onSend("planning-process", endTurn(fallbackCreate));

        var planner = new WorkflowPlannerAgent(mockLlm.toLlmClient(), new AgentRegistryMemory(),
                new ToolkitRegistry(), new SkillRegistryMemory(), workflowRegistry, dummyAgentFactory(), false);

        var result = planner.plan("novel task");

        assertEquals("Fallback Task", result.definition().name());
        assertTrue(result.inputs().isEmpty());
    }

    @Test
    void strictModeFailsWhenPlannerProposesNewAgent() {

        var planJson = """
                {
                  "type": "create",
                  "name": "Strict Task",
                  "description": "Should fail in strict mode",
                  "agents": [{"id": "invented", "name": "invented-agent", "role": "Worker"}],
                  "skills": [],
                  "params": [],
                  "steps": [
                    {"name": "step-a", "type": "agent", "agent": "invented", "instructions": "do it"}
                  ]
                }
                """;

        var mockLlm = new MockLlmClient().onSend("planning-process", endTurn(planJson));

        var planner = new WorkflowPlannerAgent(mockLlm.toLlmClient(), new AgentRegistryMemory(),
                new ToolkitRegistry(), new SkillRegistryMemory(), new WorkflowRegistryMemory(),
                dummyAgentFactory(), true);

        var ex = assertThrows(StrictPlannerException.class, () -> planner.plan("Try to invent an agent"));
        assertTrue(ex.getMessage().contains("invented"), "Should name the proposed agent in: " + ex.getMessage());
    }

    @Test
    void strictModeFailsWhenPlannerProposesNewSkill() {

        var planJson = """
                {
                  "type": "create",
                  "name": "Strict Task",
                  "description": "Should fail in strict mode",
                  "agents": [],
                  "skills": [{"id": "made-up", "name": "Made Up Skill", "instructions": "..."}],
                  "params": [],
                  "steps": [
                    {"name": "step-a", "type": "agent", "agent": "existing-agent", "instructions": "do it"}
                  ]
                }
                """;

        var mockLlm = new MockLlmClient().onSend("planning-process", endTurn(planJson));

        var agentRegistry = new AgentRegistryMemory();
        var existing = dummyAgentFactory().apply(AgentConfig.builder()
                .id("existing-agent").name("existing-agent").role("Existing").build());
        agentRegistry.register(existing);

        var planner = new WorkflowPlannerAgent(mockLlm.toLlmClient(), agentRegistry,
                new ToolkitRegistry(), new SkillRegistryMemory(), new WorkflowRegistryMemory(),
                dummyAgentFactory(), true);

        var ex = assertThrows(StrictPlannerException.class, () -> planner.plan("Try to invent a skill"));
        assertTrue(ex.getMessage().contains("Made Up Skill"),
                "Should name the proposed skill in: " + ex.getMessage());
    }

    @Test
    void strictModeFailsWhenStepReferencesUnknownAgent() {

        var planJson = """
                {
                  "type": "create",
                  "name": "Strict Task",
                  "description": "References an unknown agent",
                  "agents": [],
                  "skills": [],
                  "params": [],
                  "steps": [
                    {"name": "step-a", "type": "agent", "agent": "ghost-agent", "instructions": "do it"}
                  ]
                }
                """;

        var mockLlm = new MockLlmClient().onSend("planning-process", endTurn(planJson));

        var planner = new WorkflowPlannerAgent(mockLlm.toLlmClient(), new AgentRegistryMemory(),
                new ToolkitRegistry(), new SkillRegistryMemory(), new WorkflowRegistryMemory(),
                dummyAgentFactory(), true);

        var ex = assertThrows(StrictPlannerException.class, () -> planner.plan("Reference a missing agent"));
        assertTrue(ex.getMessage().contains("ghost-agent"),
                "Should name the unresolved agent in: " + ex.getMessage());
    }

    @Test
    void strictModeSucceedsWhenPlanOnlyUsesExistingAgents() {

        var planJson = """
                {
                  "type": "create",
                  "name": "Strict OK",
                  "description": "Uses only existing agents",
                  "agents": [],
                  "skills": [],
                  "params": [],
                  "steps": [
                    {"name": "step-a", "type": "agent", "agent": "existing-agent", "instructions": "do it"}
                  ]
                }
                """;

        var mockLlm = new MockLlmClient().onSend("planning-process", endTurn(planJson));

        var agentRegistry = new AgentRegistryMemory();
        var existing = dummyAgentFactory().apply(AgentConfig.builder()
                .id("existing-agent").name("existing-agent").role("Existing").build());
        agentRegistry.register(existing);

        var planner = new WorkflowPlannerAgent(mockLlm.toLlmClient(), agentRegistry,
                new ToolkitRegistry(), new SkillRegistryMemory(), new WorkflowRegistryMemory(),
                dummyAgentFactory(), true);

        var result = planner.plan("Use the existing agent");

        assertEquals("Strict OK", result.definition().name());
        assertEquals("existing-agent", ((WorkflowStepAgent) result.definition().steps().getFirst()).agentName());
    }
}
