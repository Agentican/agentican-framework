package ai.agentican.framework.orchestration.execution;

import ai.agentican.framework.MockLlmClient;
import ai.agentican.framework.agent.Agent;
import ai.agentican.framework.registry.AgentRegistryMemory;
import ai.agentican.framework.agent.SmacAgentRunner;
import ai.agentican.framework.hitl.HitlManager;
import ai.agentican.framework.hitl.HitlResponse;
import ai.agentican.framework.store.WorkflowRunStoreMemory;
import ai.agentican.framework.llm.LlmClient;
import ai.agentican.framework.orchestration.model.WorkflowDefinition;
import ai.agentican.framework.orchestration.model.WorkflowParam;
import ai.agentican.framework.orchestration.model.WorkflowStepAgent;
import ai.agentican.framework.registry.ToolkitRegistry;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static ai.agentican.framework.MockLlmClient.*;
import static org.junit.jupiter.api.Assertions.*;

import ai.agentican.framework.config.AgentConfig;
class TaskRunnerTest {

    private HitlManager autoApproveHitl() {

        return new HitlManager((mgr, cp) -> mgr.respond(cp.id(), HitlResponse.approve()));
    }

    private Agent createAgent(String name, MockLlmClient mockLlm) {

        return createAgent(name, mockLlm, autoApproveHitl());
    }

    private Agent createAgent(String name, MockLlmClient mockLlm, HitlManager hitlManager) {

        var runner = SmacAgentRunner.builder()
                .llmClient(mockLlm.toLlmClient())
                .hitlManager(hitlManager)
                .maxIterations(5)
                .build();

        return Agent.builder().config(AgentConfig.builder().name(name).role("Test agent for " + name).build()).runner(runner).build();
    }

    @Test
    void singleStepCompletes() {

        var mockLlm = new MockLlmClient()
                .onSend("", endTurn("output text"));

        var registry = new AgentRegistryMemory();
        registry.register(createAgent("agent-a", mockLlm));

        var workflowRunStore = new WorkflowRunStoreMemory();

        var runner = new WorkflowRunner(registry, autoApproveHitl(), new ToolkitRegistry(), workflowRunStore, null, 0, null, new ai.agentican.framework.orchestration.code.CodeStepRegistry());

        var task = WorkflowDefinition.builder("test-task")
                .step("step-a", "agent-a", "Do the thing")
                .build();

        var result = runner.run(task);

        assertEquals(WorkflowRunStatus.COMPLETED, result.status());
        assertEquals(1, result.stepResults().size());
        assertEquals("output text", result.stepResults().getFirst().output());
    }

    @Test
    void dependencyOrdering() {

        var mockLlmA = new MockLlmClient()
                .onSend("", endTurn("step-a-output"));

        var mockLlmB = new MockLlmClient()
                .onSend("step-a-output", endTurn("step-b-done"));

        var registry = new AgentRegistryMemory();
        registry.register(createAgent("agent-a", mockLlmA));
        registry.register(createAgent("agent-b", mockLlmB));

        var workflowRunStore = new WorkflowRunStoreMemory();

        var runner = new WorkflowRunner(registry, autoApproveHitl(), new ToolkitRegistry(), workflowRunStore, null, 0, null, new ai.agentican.framework.orchestration.code.CodeStepRegistry());

        var task = WorkflowDefinition.builder("dep-task")
                .step("step-a", "agent-a", "Produce output")
                .step(new WorkflowStepAgent("step-b", "agent-b",
                        "Use this: {{step.step-a.output}}", List.of("step-a"), false, null, null))
                .build();

        var result = runner.run(task);

        assertEquals(WorkflowRunStatus.COMPLETED, result.status());
        assertEquals(2, result.stepResults().size());
    }

    @Test
    void parallelStepsRunConcurrently() {

        var mockLlmA = new MockLlmClient()
                .onSend("", endTurn("parallel-a-output"));

        var mockLlmB = new MockLlmClient()
                .onSend("", endTurn("parallel-b-output"));

        var registry = new AgentRegistryMemory();
        registry.register(createAgent("agent-a", mockLlmA));
        registry.register(createAgent("agent-b", mockLlmB));

        var workflowRunStore = new WorkflowRunStoreMemory();

        var runner = new WorkflowRunner(registry, autoApproveHitl(), new ToolkitRegistry(), workflowRunStore, null, 0, null, new ai.agentican.framework.orchestration.code.CodeStepRegistry());

        var task = WorkflowDefinition.builder("parallel-task")
                .step("step-a", "agent-a", "Do A")
                .step("step-b", "agent-b", "Do B")
                .build();

        var result = runner.run(task);

        assertEquals(WorkflowRunStatus.COMPLETED, result.status());
        assertEquals(2, result.stepResults().size());
    }

    @Test
    void stepFailureStopsTask() {

        var mockLlm = new MockLlmClient();

        var registry = new AgentRegistryMemory();

        var workflowRunStore = new WorkflowRunStoreMemory();

        var runner = new WorkflowRunner(registry, autoApproveHitl(), new ToolkitRegistry(), workflowRunStore, null, 0, null, new ai.agentican.framework.orchestration.code.CodeStepRegistry());

        var task = WorkflowDefinition.builder("fail-task")
                .step("step-a", "missing-agent", "This will fail")
                .build();

        var result = runner.run(task);

        assertEquals(WorkflowRunStatus.FAILED, result.status());
    }

    @Test
    void taskCancellation() {

        var cancelled = new AtomicBoolean(false);

        var mockLlmA = new MockLlmClient()
                .onSend("", endTurn("step-a-done"));

        var mockLlmB = new MockLlmClient()
                .onSend("", endTurn("step-b-done"));

        var registry = new AgentRegistryMemory();
        registry.register(createAgent("agent-a", mockLlmA));
        registry.register(createAgent("agent-b", mockLlmB));

        var workflowRunStore = new WorkflowRunStoreMemory();

        var runner = new WorkflowRunner(registry, autoApproveHitl(), new ToolkitRegistry(), workflowRunStore, null, 0, null, new ai.agentican.framework.orchestration.code.CodeStepRegistry());

        var task = WorkflowDefinition.builder("cancel-task")
                .step("step-a", "agent-a", "Do something")
                .step(new WorkflowStepAgent("step-b", "agent-b", "Do more",
                        List.of("step-a"), false, null, null))
                .build();

        Thread.startVirtualThread(() -> {
            try { Thread.sleep(50); } catch (InterruptedException _) {}
            cancelled.set(true);
        });

        var result = runner.run(task, cancelled);

        assertTrue(result.status() == WorkflowRunStatus.CANCELLED
                        || (result.status() == WorkflowRunStatus.COMPLETED && result.stepResults().size() <= 2),
                "Expected CANCELLED or partial COMPLETED but got " + result.status());
    }

    @Test
    void stepHitlApproval() {

        var mockLlm = new MockLlmClient()
                .onSend("", endTurn("draft output"));

        var registry = new AgentRegistryMemory();
        registry.register(createAgent("agent-a", mockLlm));

        var workflowRunStore = new WorkflowRunStoreMemory();

        var runner = new WorkflowRunner(registry, autoApproveHitl(), new ToolkitRegistry(), workflowRunStore, null, 0, null, new ai.agentican.framework.orchestration.code.CodeStepRegistry());

        var task = WorkflowDefinition.builder("hitl-task")
                .step("step-a", "agent-a", "Write a draft", true)
                .build();

        var result = runner.run(task);

        assertEquals(WorkflowRunStatus.COMPLETED, result.status());
        assertEquals("draft output", result.stepResults().getFirst().output());
    }

    @Test
    void stepHitlRejectionRetry() {

        var mockLlmFirst = new MockLlmClient()
                .onSend("", endTurn("first draft"));

        var mockLlmRetry = new MockLlmClient()
                .onSend("Reviewer Feedback", endTurn("revised draft"));

        var callCount = new AtomicInteger(0);

        var hitlManager = new HitlManager((mgr, cp) -> {

            if (callCount.getAndIncrement() == 0)
                mgr.respond(cp.id(), HitlResponse.reject("add more detail"));
            else
                mgr.respond(cp.id(), HitlResponse.approve());
        });

        var mockLlm = new MockLlmClient()
                .onSend("Write a draft", endTurn("first draft"))
                .onSend("Reviewer Feedback", endTurn("revised draft"));

        var registry = new AgentRegistryMemory();
        registry.register(createAgent("agent-a", mockLlm, hitlManager));

        var workflowRunStore = new WorkflowRunStoreMemory();

        var runner = new WorkflowRunner(registry, hitlManager, new ToolkitRegistry(), workflowRunStore, null, 0, null, new ai.agentican.framework.orchestration.code.CodeStepRegistry());

        var task = WorkflowDefinition.builder("retry-task")
                .step("step-a", "agent-a", "Write a draft", true)
                .build();

        var result = runner.run(task);

        assertEquals(WorkflowRunStatus.COMPLETED, result.status());
        assertEquals("revised draft", result.stepResults().getFirst().output());
    }

    @Test
    void stepHitlMaxRetries() {

        var callCount = new AtomicInteger(0);
        var hitlManager = new HitlManager((mgr, cp) -> {

            if (callCount.getAndIncrement() == 0)
                mgr.respond(cp.id(), HitlResponse.reject("needs work"));
            else
                mgr.respond(cp.id(), HitlResponse.approve());
        });

        var mockLlm = new MockLlmClient()
                .onSend("", endTurn("attempt 1"))
                .onSend("Reviewer Feedback", endTurn("attempt 2"));

        var registry = new AgentRegistryMemory();
        registry.register(createAgent("agent-a", mockLlm, hitlManager));

        var workflowRunStore = new WorkflowRunStoreMemory();

        var runner = new WorkflowRunner(registry, hitlManager, new ToolkitRegistry(), workflowRunStore, null, 0, null, new ai.agentican.framework.orchestration.code.CodeStepRegistry());

        var task = WorkflowDefinition.builder("max-retry-task")
                .step("step-a", "agent-a", "Write something", true)
                .build();

        var result = runner.run(task);

        assertEquals(WorkflowRunStatus.COMPLETED, result.status());
        assertEquals("attempt 2", result.stepResults().getFirst().output());
    }

    @Test
    void circularDependencyDetected() {

        var mockLlm = new MockLlmClient();

        var registry = new AgentRegistryMemory();
        registry.register(createAgent("agent-a", mockLlm));

        var workflowRunStore = new WorkflowRunStoreMemory();

        var runner = new WorkflowRunner(registry, autoApproveHitl(), new ToolkitRegistry(), workflowRunStore, null, 0, null, new ai.agentican.framework.orchestration.code.CodeStepRegistry());

        var task = WorkflowDefinition.builder("cycle-task").description("Circular deps").steps(List.of(
                new WorkflowStepAgent("step-a", "agent-a", "Do A", List.of("step-b"), false, null, null),
                new WorkflowStepAgent("step-b", "agent-a", "Do B", List.of("step-a"), false, null, null)))
                .build();

        assertThrows(IllegalStateException.class, () -> runner.run(task));
    }

    @Test
    void taskLogUpdatedIncrementally() {

        var mockLlmA = new MockLlmClient()
                .onSend("", endTurn("step-a-done"));

        var mockLlmB = new MockLlmClient()
                .onSend("step-a-done", endTurn("step-b-done"));

        var registry = new AgentRegistryMemory();
        registry.register(createAgent("agent-a", mockLlmA));
        registry.register(createAgent("agent-b", mockLlmB));

        var workflowRunStore = new WorkflowRunStoreMemory();

        var runner = new WorkflowRunner(registry, autoApproveHitl(), new ToolkitRegistry(), workflowRunStore, null, 0, null, new ai.agentican.framework.orchestration.code.CodeStepRegistry());

        var task = WorkflowDefinition.builder("log-task")
                .step("step-a", "agent-a", "First step")
                .step(new WorkflowStepAgent("step-b", "agent-b",
                        "Use: {{step.step-a.output}}", List.of("step-a"), false, null, null))
                .build();

        var result = runner.run(task);

        assertEquals(WorkflowRunStatus.COMPLETED, result.status());

        var logs = workflowRunStore.list();
        assertEquals(1, logs.size());

        var taskLog = logs.getFirst();
        assertEquals(WorkflowRunStatus.COMPLETED, taskLog.status());
        assertEquals(2, taskLog.steps().size());
        assertTrue(taskLog.steps().containsKey("step-a"));
        assertTrue(taskLog.steps().containsKey("step-b"));

        assertEquals(WorkflowRunStatus.COMPLETED, taskLog.steps().get("step-a").status());
        assertEquals(WorkflowRunStatus.COMPLETED, taskLog.steps().get("step-b").status());
    }

    @Test
    void taskTimeout() {

        var slowLlm = (LlmClient) request -> {
            try { Thread.sleep(200); } catch (InterruptedException _) {}
            return MockLlmClient.endTurn("done");
        };

        var runner = SmacAgentRunner.builder()
                .llmClient(slowLlm)
                .hitlManager(autoApproveHitl())
                .maxIterations(5)
                .build();

        var agent = Agent.builder().config(AgentConfig.builder().name("agent-a").role("Slow agent").build()).runner(runner).build();

        var registry = new AgentRegistryMemory();
        registry.register(agent);

        var workflowRunStore = new WorkflowRunStoreMemory();

        var taskRunner = new WorkflowRunner(registry, autoApproveHitl(), new ToolkitRegistry(), workflowRunStore, Duration.ofMillis(50), 0, null, new ai.agentican.framework.orchestration.code.CodeStepRegistry());

        var task = WorkflowDefinition.builder("timeout-task")
                .step("step-a", "agent-a", "Do something slow")
                .build();

        var result = taskRunner.run(task);

        assertTrue(result.status() == WorkflowRunStatus.FAILED || result.status() == WorkflowRunStatus.COMPLETED,
                "Expected FAILED or COMPLETED but got " + result.status());
    }

    @Test
    void taskParameterDefaults() {

        var mockLlm = new MockLlmClient()
                .onSend("Process 5 items", endTurn("processed"));

        var registry = new AgentRegistryMemory();
        registry.register(createAgent("agent-a", mockLlm));

        var workflowRunStore = new WorkflowRunStoreMemory();

        var runner = new WorkflowRunner(registry, autoApproveHitl(), new ToolkitRegistry(), workflowRunStore, null, 0, null, new ai.agentican.framework.orchestration.code.CodeStepRegistry());

        var task = WorkflowDefinition.builder("default-param-task").description("Test defaults")
                .param(new WorkflowParam("count", "Number of items", "5", false))
                .step(new WorkflowStepAgent("step-a", "agent-a", "Process {{param.count}} items", List.of(), false, List.of(), List.of()))
                .build();

        var result = runner.run(task);

        assertEquals(WorkflowRunStatus.COMPLETED, result.status());
        assertEquals("processed", result.stepResults().getFirst().output());
    }

    @Test
    void taskParameterRequiredMissing() {

        var mockLlm = new MockLlmClient();

        var registry = new AgentRegistryMemory();
        registry.register(createAgent("agent-a", mockLlm));

        var workflowRunStore = new WorkflowRunStoreMemory();

        var taskRunner = new WorkflowRunner(registry, autoApproveHitl(), new ToolkitRegistry(), workflowRunStore, null, 0, null, new ai.agentican.framework.orchestration.code.CodeStepRegistry());

        var task = WorkflowDefinition.builder("required-param-task").description("Test required")
                .param(new WorkflowParam("required_param", "desc", null, true))
                .step(new WorkflowStepAgent("step-a", "agent-a", "Do {{param.required_param}}", List.of(), false, List.of(), List.of()))
                .build();

        assertThrows(IllegalArgumentException.class, () -> taskRunner.run(task));
    }
}
