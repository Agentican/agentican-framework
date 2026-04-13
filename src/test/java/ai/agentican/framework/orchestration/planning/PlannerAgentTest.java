package ai.agentican.framework.orchestration.planning;

import ai.agentican.framework.MockLlmClient;
import ai.agentican.framework.MockToolkit;
import ai.agentican.framework.agent.Agent;
import ai.agentican.framework.agent.AgentRegistry;
import ai.agentican.framework.agent.AgentResult;
import ai.agentican.framework.agent.AgentStatus;
import ai.agentican.framework.config.AgentConfig;
import ai.agentican.framework.state.RunLog;
import ai.agentican.framework.orchestration.model.PlanStepAgent;
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

        return config -> new Agent(config.name(), config.role(), config.skills(),
                (agent, task, activeSkills, toolkits, taskId, stepId, stepName) ->
                        new AgentResult(AgentStatus.COMPLETED, new RunLog(Ids.generate(), 0, (String) null)));
    }

    @Test
    void planCreatesTaskAndRegistersAgents() {

        var planJson = """
                {
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

        var agentRegistry = new AgentRegistry();
        var toolkitRegistry = new ToolkitRegistry();

        var planner = new PlannerAgent(mockLlm.toLlmClient(), agentRegistry, toolkitRegistry,
                dummyAgentFactory());

        var task = planner.plan("Do a task");

        assertEquals("Test Task", task.name());
        assertEquals(1, task.steps().size());
        assertEquals("step-a", task.steps().getFirst().name());
        assertTrue(agentRegistry.isRegistered("test-agent"));
    }

    @Test
    void planRefinesAgentStepsWithToolContext() {

        var planJson = """
                {
                  "name": "Refined Task",
                  "description": "A test with toolkits",
                  "agents": [{"name": "tool-agent", "role": "Tool user"}],
                  "paramConfigs": [],
                  "stepConfigs": [
                    {"name": "tool-step", "type": "agent", "agent": "tool-agent", "instructions": "Use the tool", "toolkits": ["test-toolkit"]}
                  ]
                }
                """;

        var mockLlm = new MockLlmClient()
                .onSend("planning-process", endTurn(planJson))
                .onSend("<name>tool-step</name>", endTurn("Refined: use MY_TOOL with param q"));

        var agentRegistry = new AgentRegistry();

        var toolkitRegistry = new ToolkitRegistry();
        var toolkit = new MockToolkit(List.of(
                new ToolDefinition("MY_TOOL", "A test tool", Map.of("q", Map.of("type", "string")))));
        toolkitRegistry.register("test-toolkit", toolkit);

        var planner = new PlannerAgent(mockLlm.toLlmClient(), agentRegistry, toolkitRegistry,
                dummyAgentFactory());

        var task = planner.plan("Do a task with tools");

        var step = (PlanStepAgent) task.steps().getFirst();
        assertEquals("Refined: use MY_TOOL with param q", step.instructions());
    }

    @Test
    void planSkipsRefinementForStepsWithoutToolkits() {

        var planJson = """
                {
                  "name": "No-Tool Task",
                  "description": "A test without toolkits",
                  "agents": [{"name": "plain-agent", "role": "Worker"}],
                  "paramConfigs": [],
                  "stepConfigs": [
                    {"name": "plain-step", "type": "agent", "agent": "plain-agent", "instructions": "Just think", "toolkits": []}
                  ]
                }
                """;

        // Only one LLM call needed (pass 1). No refinement call since no toolkits.
        var mockLlm = new MockLlmClient()
                .onSend("planning-process", endTurn(planJson));

        var agentRegistry = new AgentRegistry();
        var toolkitRegistry = new ToolkitRegistry();

        var planner = new PlannerAgent(mockLlm.toLlmClient(), agentRegistry, toolkitRegistry,
                dummyAgentFactory());

        var task = planner.plan("Think about something");

        var step = (PlanStepAgent) task.steps().getFirst();
        assertEquals("Just think", step.instructions());
    }

    @Test
    void planWithLoopStep() {

        var planJson = """
                {
                  "name": "Loop Task",
                  "description": "A test with loop",
                  "agents": [{"name": "producer-agent", "role": "Producer"}, {"name": "body-agent", "role": "Processor"}],
                  "paramConfigs": [],
                  "stepConfigs": [
                    {"name": "produce", "type": "agent", "agent": "producer-agent", "instructions": "Produce items", "toolkits": ["test-toolkit"]},
                    {"name": "process-loop", "type": "loop", "over": "produce", "stepConfigs": [
                      {"name": "process-item", "type": "agent", "agent": "body-agent", "instructions": "Process {{item}}", "toolkits": ["test-toolkit"]}
                    ]}
                  ]
                }
                """;

        var refinementJson = """
                {
                  "setup_step": null,
                  "body_steps": [{"name": "process-item", "instructions": "Refined: process {{item}} with MY_TOOL"}],
                  "loop_over": "produce"
                }
                """;

        var mockLlm = new MockLlmClient()
                .onSend("planning-process", endTurn(planJson))
                .onSend("<name>produce</name>", endTurn("Refined: produce items with MY_TOOL"))
                .onSend("<name>process-item</name>", endTurn("Refined: process with tool"))
                .onSend("loop step", endTurn(refinementJson));

        var agentRegistry = new AgentRegistry();

        var toolkitRegistry = new ToolkitRegistry();
        var toolkit = new MockToolkit(List.of(
                new ToolDefinition("MY_TOOL", "A test tool", Map.of())));
        toolkitRegistry.register("test-toolkit", toolkit);

        var planner = new PlannerAgent(mockLlm.toLlmClient(), agentRegistry, toolkitRegistry,
                dummyAgentFactory());

        var task = planner.plan("Produce and process items");

        assertEquals("Loop Task", task.name());
        assertTrue(task.steps().size() >= 2);
        assertTrue(agentRegistry.isRegistered("producer-agent"));
        assertTrue(agentRegistry.isRegistered("body-agent"));
    }
}
