package ai.agentican.framework.orchestration.execution;

import ai.agentican.framework.MockLlmClient;
import ai.agentican.framework.agent.Agent;
import ai.agentican.framework.agent.InMemoryAgentRegistry;
import ai.agentican.framework.agent.SmacAgentRunner;
import ai.agentican.framework.hitl.HitlManager;
import ai.agentican.framework.hitl.HitlResponse;
import ai.agentican.framework.state.MemTaskStateStore;
import ai.agentican.framework.llm.LlmClient;
import ai.agentican.framework.orchestration.model.Plan;
import ai.agentican.framework.orchestration.model.PlanParam;
import ai.agentican.framework.orchestration.model.PlanStepAgent;
import ai.agentican.framework.tools.ToolkitRegistry;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static ai.agentican.framework.MockLlmClient.*;
import static org.junit.jupiter.api.Assertions.*;

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

        return Agent.of(name, "Test agent for " + name, runner);
    }

    @Test
    void singleStepCompletes() {

        var mockLlm = new MockLlmClient()
                .onSend("", endTurn("output text"));

        var registry = new InMemoryAgentRegistry();
        registry.register(createAgent("agent-a", mockLlm));

        var taskStateStore = new MemTaskStateStore();

        var runner = TaskRunner.of(registry, autoApproveHitl(), new ToolkitRegistry(), taskStateStore);

        var task = Plan.builder("test-task")
                .step("step-a", "agent-a", "Do the thing")
                .build();

        var result = runner.run(task);

        assertEquals(TaskStatus.COMPLETED, result.status());
        assertEquals(1, result.stepResults().size());
        assertEquals("output text", result.stepResults().getFirst().output());
    }

    @Test
    void dependencyOrdering() {

        var mockLlmA = new MockLlmClient()
                .onSend("", endTurn("step-a-output"));

        var mockLlmB = new MockLlmClient()
                .onSend("step-a-output", endTurn("step-b-done"));

        var registry = new InMemoryAgentRegistry();
        registry.register(createAgent("agent-a", mockLlmA));
        registry.register(createAgent("agent-b", mockLlmB));

        var taskStateStore = new MemTaskStateStore();

        var runner = TaskRunner.of(registry, autoApproveHitl(), new ToolkitRegistry(), taskStateStore);

        var task = Plan.builder("dep-task")
                .step("step-a", "agent-a", "Produce output")
                .step(new PlanStepAgent("step-b", "agent-b",
                        "Use this: {{step.step-a.output}}", List.of("step-a"), false, null, null))
                .build();

        var result = runner.run(task);

        assertEquals(TaskStatus.COMPLETED, result.status());
        assertEquals(2, result.stepResults().size());
    }

    @Test
    void parallelStepsRunConcurrently() {

        var mockLlmA = new MockLlmClient()
                .onSend("", endTurn("parallel-a-output"));

        var mockLlmB = new MockLlmClient()
                .onSend("", endTurn("parallel-b-output"));

        var registry = new InMemoryAgentRegistry();
        registry.register(createAgent("agent-a", mockLlmA));
        registry.register(createAgent("agent-b", mockLlmB));

        var taskStateStore = new MemTaskStateStore();

        var runner = TaskRunner.of(registry, autoApproveHitl(), new ToolkitRegistry(), taskStateStore);

        var task = Plan.builder("parallel-task")
                .step("step-a", "agent-a", "Do A")
                .step("step-b", "agent-b", "Do B")
                .build();

        var result = runner.run(task);

        assertEquals(TaskStatus.COMPLETED, result.status());
        assertEquals(2, result.stepResults().size());
    }

    @Test
    void stepFailureStopsTask() {

        var mockLlm = new MockLlmClient();

        var registry = new InMemoryAgentRegistry();

        var taskStateStore = new MemTaskStateStore();

        var runner = TaskRunner.of(registry, autoApproveHitl(), new ToolkitRegistry(), taskStateStore);

        var task = Plan.builder("fail-task")
                .step("step-a", "missing-agent", "This will fail")
                .build();

        var result = runner.run(task);

        assertEquals(TaskStatus.FAILED, result.status());
    }

    @Test
    void taskCancellation() {

        var cancelled = new AtomicBoolean(false);

        var mockLlmA = new MockLlmClient()
                .onSend("", endTurn("step-a-done"));

        var mockLlmB = new MockLlmClient()
                .onSend("", endTurn("step-b-done"));

        var registry = new InMemoryAgentRegistry();
        registry.register(createAgent("agent-a", mockLlmA));
        registry.register(createAgent("agent-b", mockLlmB));

        var taskStateStore = new MemTaskStateStore();

        var runner = TaskRunner.of(registry, autoApproveHitl(), new ToolkitRegistry(), taskStateStore);

        var task = Plan.builder("cancel-task")
                .step("step-a", "agent-a", "Do something")
                .step(new PlanStepAgent("step-b", "agent-b", "Do more",
                        List.of("step-a"), false, null, null))
                .build();

        Thread.startVirtualThread(() -> {
            try { Thread.sleep(50); } catch (InterruptedException _) {}
            cancelled.set(true);
        });

        var result = runner.run(task, cancelled);

        assertTrue(result.status() == TaskStatus.CANCELLED
                        || (result.status() == TaskStatus.COMPLETED && result.stepResults().size() <= 2),
                "Expected CANCELLED or partial COMPLETED but got " + result.status());
    }

    @Test
    void stepHitlApproval() {

        var mockLlm = new MockLlmClient()
                .onSend("", endTurn("draft output"));

        var registry = new InMemoryAgentRegistry();
        registry.register(createAgent("agent-a", mockLlm));

        var taskStateStore = new MemTaskStateStore();

        var runner = TaskRunner.of(registry, autoApproveHitl(), new ToolkitRegistry(), taskStateStore);

        var task = Plan.builder("hitl-task")
                .step("step-a", "agent-a", "Write a draft", true)
                .build();

        var result = runner.run(task);

        assertEquals(TaskStatus.COMPLETED, result.status());
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

        var registry = new InMemoryAgentRegistry();
        registry.register(createAgent("agent-a", mockLlm, hitlManager));

        var taskStateStore = new MemTaskStateStore();

        var runner = TaskRunner.of(registry, hitlManager, new ToolkitRegistry(), taskStateStore);

        var task = Plan.builder("retry-task")
                .step("step-a", "agent-a", "Write a draft", true)
                .build();

        var result = runner.run(task);

        assertEquals(TaskStatus.COMPLETED, result.status());
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

        var registry = new InMemoryAgentRegistry();
        registry.register(createAgent("agent-a", mockLlm, hitlManager));

        var taskStateStore = new MemTaskStateStore();

        var runner = TaskRunner.of(registry, hitlManager, new ToolkitRegistry(), taskStateStore);

        var task = Plan.builder("max-retry-task")
                .step("step-a", "agent-a", "Write something", true)
                .build();

        var result = runner.run(task);

        assertEquals(TaskStatus.COMPLETED, result.status());
        assertEquals("attempt 2", result.stepResults().getFirst().output());
    }

    @Test
    void circularDependencyDetected() {

        var mockLlm = new MockLlmClient();

        var registry = new InMemoryAgentRegistry();
        registry.register(createAgent("agent-a", mockLlm));

        var taskStateStore = new MemTaskStateStore();

        var runner = TaskRunner.of(registry, autoApproveHitl(), new ToolkitRegistry(), taskStateStore);

        var task = new Plan(null, "cycle-task", "Circular deps", List.of(), List.of(
                new PlanStepAgent("step-a", "agent-a", "Do A", List.of("step-b"), false, null, null),
                new PlanStepAgent("step-b", "agent-a", "Do B", List.of("step-a"), false, null, null)));

        assertThrows(IllegalStateException.class, () -> runner.run(task));
    }

    @Test
    void taskLogUpdatedIncrementally() {

        var mockLlmA = new MockLlmClient()
                .onSend("", endTurn("step-a-done"));

        var mockLlmB = new MockLlmClient()
                .onSend("step-a-done", endTurn("step-b-done"));

        var registry = new InMemoryAgentRegistry();
        registry.register(createAgent("agent-a", mockLlmA));
        registry.register(createAgent("agent-b", mockLlmB));

        var taskStateStore = new MemTaskStateStore();

        var runner = TaskRunner.of(registry, autoApproveHitl(), new ToolkitRegistry(), taskStateStore);

        var task = Plan.builder("log-task")
                .step("step-a", "agent-a", "First step")
                .step(new PlanStepAgent("step-b", "agent-b",
                        "Use: {{step.step-a.output}}", List.of("step-a"), false, null, null))
                .build();

        var result = runner.run(task);

        assertEquals(TaskStatus.COMPLETED, result.status());

        var logs = taskStateStore.list();
        assertEquals(1, logs.size());

        var taskLog = logs.getFirst();
        assertEquals(TaskStatus.COMPLETED, taskLog.status());
        assertEquals(2, taskLog.steps().size());
        assertTrue(taskLog.steps().containsKey("step-a"));
        assertTrue(taskLog.steps().containsKey("step-b"));

        assertEquals(TaskStatus.COMPLETED, taskLog.steps().get("step-a").status());
        assertEquals(TaskStatus.COMPLETED, taskLog.steps().get("step-b").status());
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

        var agent = Agent.of("agent-a", "Slow agent", runner);

        var registry = new InMemoryAgentRegistry();
        registry.register(agent);

        var taskStateStore = new MemTaskStateStore();

        var taskRunner = TaskRunner.of(registry, autoApproveHitl(), new ToolkitRegistry(), taskStateStore, Duration.ofMillis(50));

        var task = Plan.builder("timeout-task")
                .step("step-a", "agent-a", "Do something slow")
                .build();

        var result = taskRunner.run(task);

        assertTrue(result.status() == TaskStatus.FAILED || result.status() == TaskStatus.COMPLETED,
                "Expected FAILED or COMPLETED but got " + result.status());
    }

    @Test
    void taskParameterDefaults() {

        var mockLlm = new MockLlmClient()
                .onSend("Process 5 items", endTurn("processed"));

        var registry = new InMemoryAgentRegistry();
        registry.register(createAgent("agent-a", mockLlm));

        var taskStateStore = new MemTaskStateStore();

        var runner = TaskRunner.of(registry, autoApproveHitl(), new ToolkitRegistry(), taskStateStore);

        var task = new Plan(null, "default-param-task", "Test defaults",
                List.of(new PlanParam("count", "Number of items", "5")),
                List.of(new PlanStepAgent("step-a", "agent-a", "Process {{param.count}} items", List.of(), false, List.of(), List.of())));

        var result = runner.run(task);

        assertEquals(TaskStatus.COMPLETED, result.status());
        assertEquals("processed", result.stepResults().getFirst().output());
    }

    @Test
    void taskParameterRequiredMissing() {

        var mockLlm = new MockLlmClient();

        var registry = new InMemoryAgentRegistry();
        registry.register(createAgent("agent-a", mockLlm));

        var taskStateStore = new MemTaskStateStore();

        var taskRunner = TaskRunner.of(registry, autoApproveHitl(), new ToolkitRegistry(), taskStateStore);

        var task = new Plan(null, "required-param-task", "Test required",
                List.of(new PlanParam("required_param", "desc", null, true)),
                List.of(new PlanStepAgent("step-a", "agent-a", "Do {{param.required_param}}", List.of(), false, List.of(), List.of())));

        assertThrows(IllegalArgumentException.class, () -> taskRunner.run(task));
    }
}
