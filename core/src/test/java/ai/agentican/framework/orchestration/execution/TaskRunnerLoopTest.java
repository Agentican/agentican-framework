package ai.agentican.framework.orchestration.execution;

import ai.agentican.framework.MockLlmClient;
import ai.agentican.framework.agent.Agent;
import ai.agentican.framework.registry.AgentRegistryMemory;
import ai.agentican.framework.agent.SmacAgentRunner;
import ai.agentican.framework.hitl.HitlManager;
import ai.agentican.framework.hitl.HitlResponse;
import ai.agentican.framework.store.WorkflowRunStoreMemory;
import ai.agentican.framework.orchestration.model.WorkflowDefinition;
import ai.agentican.framework.registry.ToolkitRegistry;
import org.junit.jupiter.api.Test;

import static ai.agentican.framework.MockLlmClient.*;
import static org.junit.jupiter.api.Assertions.*;

import ai.agentican.framework.config.AgentConfig;
import ai.agentican.framework.orchestration.code.CodeStepRegistry;
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

        return Agent.builder().config(AgentConfig.builder().name(name).id(name).role("Test agent for " + name).build()).runner(runner).build();
    }

    @Test
    void loopIteratesOverJsonArray() {

        var producerLlm = new MockLlmClient()
                .onSend("", endTurn("[{\"name\": \"Alice\"}, {\"name\": \"Bob\"}]"));

        var bodyLlm = new MockLlmClient()
                .onSend("Alice", endTurn("Processed Alice"))
                .onSend("Bob", endTurn("Processed Bob"));

        var registry = new AgentRegistryMemory();
        registry.register(createAgent("producer-agent", producerLlm));
        registry.register(createAgent("body-agent", bodyLlm));

        var runner = new WorkflowRunner(registry, autoApproveHitl(), new ToolkitRegistry(), new WorkflowRunStoreMemory(), null, null, 0, null, new CodeStepRegistry());

        var task = WorkflowDefinition.builder("loop-task", "loop-task")
                .step().name("produce").agent("producer-agent").instructions("Produce a JSON array").end()
                .loop()
                        .name("loop-step")
                        .over("produce")
                        .step().name("process").agent("body-agent").instructions("Process {{item.name}}").end()
                        .end()
                .build();

        var result = runner.run(task);

        assertEquals(WorkflowRunStatus.COMPLETED, result.status());
        assertEquals(2, result.stepResults().size());

        var loopOutput = result.stepResults().get(1).output();
        assertTrue(loopOutput.contains("Processed Alice"), "Expected 'Processed Alice' in: " + loopOutput);
        assertTrue(loopOutput.contains("Processed Bob"), "Expected 'Processed Bob' in: " + loopOutput);
    }

    @Test
    void loopWithZeroItems() {

        var producerLlm = new MockLlmClient()
                .onSend("", endTurn("[]"));

        var registry = new AgentRegistryMemory();
        registry.register(createAgent("producer-agent", producerLlm));
        registry.register(createAgent("body-agent", new MockLlmClient()));

        var runner = new WorkflowRunner(registry, autoApproveHitl(), new ToolkitRegistry(), new WorkflowRunStoreMemory(), null, null, 0, null, new CodeStepRegistry());

        var task = WorkflowDefinition.builder("empty-loop-task", "empty-loop-task")
                .step().name("produce").agent("producer-agent").instructions("Produce an empty array").end()
                .loop()
                        .name("loop-step")
                        .over("produce")
                        .step().name("process").agent("body-agent").instructions("Process {{item}}").end()
                        .end()
                .build();

        var result = runner.run(task);

        assertEquals(WorkflowRunStatus.COMPLETED, result.status());

        var loopStepResult = result.stepResults().get(1);
        assertEquals("", loopStepResult.output());
    }

    @Test
    void loopItemPlaceholdersResolved() {

        var producerLlm = new MockLlmClient()
                .onSend("", endTurn("[{\"id\": \"123\", \"title\": \"Test\"}]"));

        var bodyLlm = new MockLlmClient()
                .onSend("Process item 123", endTurn("Done with 123"));

        var registry = new AgentRegistryMemory();
        registry.register(createAgent("producer-agent", producerLlm));
        registry.register(createAgent("body-agent", bodyLlm));

        var runner = new WorkflowRunner(registry, autoApproveHitl(), new ToolkitRegistry(), new WorkflowRunStoreMemory(), null, null, 0, null, new CodeStepRegistry());

        var task = WorkflowDefinition.builder("placeholder-loop-task", "placeholder-loop-task")
                .step().name("produce").agent("producer-agent").instructions("Produce items").end()
                .loop()
                        .name("loop-step")
                        .over("produce")
                        .step().name("process").agent("body-agent")
                                .instructions("Process item {{item.id}} titled {{item.title}}").end()
                        .end()
                .build();

        var result = runner.run(task);

        assertEquals(WorkflowRunStatus.COMPLETED, result.status());
        assertTrue(result.stepResults().get(1).output().contains("Done with 123"));
    }

    @Test
    void loopAggregatesIterationOutputs() {

        var producerLlm = new MockLlmClient()
                .onSend("", endTurn("[{\"id\": \"1\"}, {\"id\": \"2\"}]"));

        var bodyLlm = new MockLlmClient()
                .onSend("item 1", endTurn("Result for item 1"))
                .onSend("item 2", endTurn("Result for item 2"));

        var registry = new AgentRegistryMemory();
        registry.register(createAgent("producer-agent", producerLlm));
        registry.register(createAgent("body-agent", bodyLlm));

        var runner = new WorkflowRunner(registry, autoApproveHitl(), new ToolkitRegistry(), new WorkflowRunStoreMemory(), null, null, 0, null, new CodeStepRegistry());

        var task = WorkflowDefinition.builder("aggregate-loop-task", "aggregate-loop-task")
                .step().name("produce").agent("producer-agent").instructions("Produce items").end()
                .loop()
                        .name("loop-step")
                        .over("produce")
                        .step().name("process").agent("body-agent")
                                .instructions("Process item {{item.id}}").end()
                        .end()
                .build();

        var result = runner.run(task);

        assertEquals(WorkflowRunStatus.COMPLETED, result.status());

        var loopOutput = result.stepResults().get(1).output();
        assertTrue(loopOutput.contains("## Iteration 1"), "Expected iteration 1 header in: " + loopOutput);
        assertTrue(loopOutput.contains("## Iteration 2"), "Expected iteration 2 header in: " + loopOutput);
    }

    @Test
    void loopMissingUpstreamOutput() {

        var producerLlm = new MockLlmClient()
                .onSend("", endTurn("some output"));

        var registry = new AgentRegistryMemory();
        registry.register(createAgent("producer-agent", producerLlm));
        registry.register(createAgent("body-agent", new MockLlmClient()));

        var runner = new WorkflowRunner(registry, autoApproveHitl(), new ToolkitRegistry(), new WorkflowRunStoreMemory(), null, null, 0, null, new CodeStepRegistry());

        var task = WorkflowDefinition.builder("missing-upstream-task", "missing-upstream-task")
                .step().name("produce").agent("producer-agent").instructions("Produce something").end()
                .loop()
                        .name("loop-step")
                        .over("nonexistent")
                        .step().name("process").agent("body-agent").instructions("Process {{item}}").end()
                        .end()
                .build();

        var result = runner.run(task);

        assertEquals(WorkflowRunStatus.FAILED, result.status());
    }
}
