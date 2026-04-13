package ai.agentican.framework.orchestration.execution;

import ai.agentican.framework.MockLlmClient;
import ai.agentican.framework.agent.Agent;
import ai.agentican.framework.agent.AgentRegistry;
import ai.agentican.framework.agent.SmacAgentRunner;
import ai.agentican.framework.hitl.HitlManager;
import ai.agentican.framework.hitl.HitlResponse;
import ai.agentican.framework.state.MemTaskStateStore;
import ai.agentican.framework.orchestration.model.Plan;
import ai.agentican.framework.orchestration.model.PlanStepAgent;
import ai.agentican.framework.tools.ToolkitRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;

import static ai.agentican.framework.MockLlmClient.*;
import static org.junit.jupiter.api.Assertions.*;

class TaskRunnerLoopTest {

    private HitlManager autoApproveHitl() {

        return new HitlManager((mgr, cp) -> mgr.respond(cp.id(), HitlResponse.approve()));
    }

    private Agent createAgent(String name, MockLlmClient mockLlm) {

        var runner = SmacAgentRunner.builder()
                .llmClient(mockLlm.toLlmClient())
                .hitlManager(autoApproveHitl())
                .maxIterations(5)
                .build();

        return new Agent(name, "Test agent for " + name, List.of(), runner);
    }

    @Test
    void loopIteratesOverJsonArray() {

        var producerLlm = new MockLlmClient()
                .onSend("", endTurn("[{\"name\": \"Alice\"}, {\"name\": \"Bob\"}]"));

        var bodyLlm = new MockLlmClient()
                .onSend("Alice", endTurn("Processed Alice"))
                .onSend("Bob", endTurn("Processed Bob"));

        var registry = new AgentRegistry();
        registry.register(createAgent("producer-agent", producerLlm));
        registry.register(createAgent("body-agent", bodyLlm));

        var runner = TaskRunner.of(registry, autoApproveHitl(), new ToolkitRegistry(), new MemTaskStateStore());

        var task = Plan.builder("loop-task")
                .step("produce", "producer-agent", "Produce a JSON array")
                .loop("loop-step", loop -> loop
                        .over("produce")
                        .step(new PlanStepAgent("process", "body-agent",
                                "Process {{item.name}}", null, false, null, null)))
                .build();

        var result = runner.run(task);

        assertEquals(TaskStatus.COMPLETED, result.status());
        assertEquals(2, result.stepResults().size());

        var loopOutput = result.stepResults().get(1).output();
        assertTrue(loopOutput.contains("Processed Alice"), "Expected 'Processed Alice' in: " + loopOutput);
        assertTrue(loopOutput.contains("Processed Bob"), "Expected 'Processed Bob' in: " + loopOutput);
    }

    @Test
    void loopWithZeroItems() {

        var producerLlm = new MockLlmClient()
                .onSend("", endTurn("[]"));

        var registry = new AgentRegistry();
        registry.register(createAgent("producer-agent", producerLlm));
        registry.register(createAgent("body-agent", new MockLlmClient()));

        var runner = TaskRunner.of(registry, autoApproveHitl(), new ToolkitRegistry(), new MemTaskStateStore());

        var task = Plan.builder("empty-loop-task")
                .step("produce", "producer-agent", "Produce an empty array")
                .loop("loop-step", loop -> loop
                        .over("produce")
                        .step(new PlanStepAgent("process", "body-agent",
                                "Process {{item}}", null, false, null, null)))
                .build();

        var result = runner.run(task);

        assertEquals(TaskStatus.COMPLETED, result.status());

        var loopStepResult = result.stepResults().get(1);
        assertEquals("", loopStepResult.output());
    }

    @Test
    void loopItemPlaceholdersResolved() {

        var producerLlm = new MockLlmClient()
                .onSend("", endTurn("[{\"id\": \"123\", \"title\": \"Test\"}]"));

        var bodyLlm = new MockLlmClient()
                .onSend("Process item 123", endTurn("Done with 123"));

        var registry = new AgentRegistry();
        registry.register(createAgent("producer-agent", producerLlm));
        registry.register(createAgent("body-agent", bodyLlm));

        var runner = TaskRunner.of(registry, autoApproveHitl(), new ToolkitRegistry(), new MemTaskStateStore());

        var task = Plan.builder("placeholder-loop-task")
                .step("produce", "producer-agent", "Produce items")
                .loop("loop-step", loop -> loop
                        .over("produce")
                        .step(new PlanStepAgent("process", "body-agent",
                                "Process item {{item.id}} titled {{item.title}}", null, false, null, null)))
                .build();

        var result = runner.run(task);

        assertEquals(TaskStatus.COMPLETED, result.status());
        assertTrue(result.stepResults().get(1).output().contains("Done with 123"));
    }

    @Test
    void loopAggregatesIterationOutputs() {

        var producerLlm = new MockLlmClient()
                .onSend("", endTurn("[{\"id\": \"1\"}, {\"id\": \"2\"}]"));

        var bodyLlm = new MockLlmClient()
                .onSend("item 1", endTurn("Result for item 1"))
                .onSend("item 2", endTurn("Result for item 2"));

        var registry = new AgentRegistry();
        registry.register(createAgent("producer-agent", producerLlm));
        registry.register(createAgent("body-agent", bodyLlm));

        var runner = TaskRunner.of(registry, autoApproveHitl(), new ToolkitRegistry(), new MemTaskStateStore());

        var task = Plan.builder("aggregate-loop-task")
                .step("produce", "producer-agent", "Produce items")
                .loop("loop-step", loop -> loop
                        .over("produce")
                        .step(new PlanStepAgent("process", "body-agent",
                                "Process item {{item.id}}", null, false, null, null)))
                .build();

        var result = runner.run(task);

        assertEquals(TaskStatus.COMPLETED, result.status());

        var loopOutput = result.stepResults().get(1).output();
        assertTrue(loopOutput.contains("## Iteration 1"), "Expected iteration 1 header in: " + loopOutput);
        assertTrue(loopOutput.contains("## Iteration 2"), "Expected iteration 2 header in: " + loopOutput);
    }

    @Test
    void loopMissingUpstreamOutput() {

        // Producer returns some output, but the loop references a nonexistent step
        var producerLlm = new MockLlmClient()
                .onSend("", endTurn("some output"));

        var registry = new AgentRegistry();
        registry.register(createAgent("producer-agent", producerLlm));
        registry.register(createAgent("body-agent", new MockLlmClient()));

        var runner = TaskRunner.of(registry, autoApproveHitl(), new ToolkitRegistry(), new MemTaskStateStore());

        var task = Plan.builder("missing-upstream-task")
                .step("produce", "producer-agent", "Produce something")
                .loop("loop-step", loop -> loop
                        .over("nonexistent")
                        .step(new PlanStepAgent("process", "body-agent",
                                "Process {{item}}", null, false, null, null)))
                .build();

        var result = runner.run(task);

        assertEquals(TaskStatus.FAILED, result.status());
    }
}
