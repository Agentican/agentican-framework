package ai.agentican.framework.orchestration.execution;

import ai.agentican.framework.MockLlmClient;
import ai.agentican.framework.agent.Agent;
import ai.agentican.framework.registry.AgentRegistryMemory;
import ai.agentican.framework.agent.SmacAgentRunner;
import ai.agentican.framework.hitl.HitlManager;
import ai.agentican.framework.hitl.HitlResponse;
import ai.agentican.framework.store.TaskStateStoreMemory;
import ai.agentican.framework.orchestration.model.Plan;
import ai.agentican.framework.orchestration.model.PlanStepAgent;
import ai.agentican.framework.registry.ToolkitRegistry;
import org.junit.jupiter.api.Test;

import static ai.agentican.framework.MockLlmClient.*;
import static org.junit.jupiter.api.Assertions.*;

import ai.agentican.framework.config.AgentConfig;
class TaskRunnerBranchTest {

    private HitlManager autoApproveHitl() {

        return new HitlManager((mgr, cp) -> mgr.respond(cp.id(), HitlResponse.approve()));
    }

    private Agent createAgent(String name, MockLlmClient mockLlm) {

        var runner = SmacAgentRunner.builder()
                .llmClient(mockLlm.toLlmClient())
                .hitlManager(autoApproveHitl())
                .maxIterations(5)
                .build();

        return Agent.builder().config(AgentConfig.builder().name(name).role("Test agent for " + name).build()).runner(runner).build();
    }

    @Test
    void branchSelectsExactMatch() {

        var producerLlm = new MockLlmClient()
                .onSend("", endTurn("path-a"));

        var pathALlm = new MockLlmClient()
                .onSend("", endTurn("A result"));

        var pathBLlm = new MockLlmClient()
                .onSend("", endTurn("B result"));

        var registry = new AgentRegistryMemory();
        registry.register(createAgent("producer-agent", producerLlm));
        registry.register(createAgent("path-a-agent", pathALlm));
        registry.register(createAgent("path-b-agent", pathBLlm));

        var runner = new TaskRunner(registry, autoApproveHitl(), new ToolkitRegistry(), new TaskStateStoreMemory(), null, 0, null, new ai.agentican.framework.orchestration.code.CodeStepRegistry());

        var task = Plan.builder("branch-task")
                .step("decide", "producer-agent", "Pick a path")
                .branch("branch-step", branch -> branch
                        .from("decide")
                        .path("path-a", new PlanStepAgent("do-a", "path-a-agent",
                                "Execute path A", null, false, null, null))
                        .path("path-b", new PlanStepAgent("do-b", "path-b-agent",
                                "Execute path B", null, false, null, null)))
                .build();

        var result = runner.run(task);

        assertEquals(TaskStatus.COMPLETED, result.status());
        assertEquals("A result", result.stepResults().get(1).output());
    }

    @Test
    void branchUsesDefault() {

        var producerLlm = new MockLlmClient()
                .onSend("", endTurn("unknown"));

        var pathALlm = new MockLlmClient()
                .onSend("", endTurn("A result"));

        var pathBLlm = new MockLlmClient()
                .onSend("", endTurn("B result"));

        var registry = new AgentRegistryMemory();
        registry.register(createAgent("producer-agent", producerLlm));
        registry.register(createAgent("path-a-agent", pathALlm));
        registry.register(createAgent("path-b-agent", pathBLlm));

        var runner = new TaskRunner(registry, autoApproveHitl(), new ToolkitRegistry(), new TaskStateStoreMemory(), null, 0, null, new ai.agentican.framework.orchestration.code.CodeStepRegistry());

        var task = Plan.builder("default-branch-task")
                .step("decide", "producer-agent", "Pick a path")
                .branch("branch-step", branch -> branch
                        .from("decide")
                        .path("path-a", new PlanStepAgent("do-a", "path-a-agent",
                                "Execute path A", null, false, null, null))
                        .path("path-b", new PlanStepAgent("do-b", "path-b-agent",
                                "Execute path B", null, false, null, null))
                        .defaultPath("path-b"))
                .build();

        var result = runner.run(task);

        assertEquals(TaskStatus.COMPLETED, result.status());
        assertEquals("B result", result.stepResults().get(1).output());
    }

    @Test
    void branchNoMatchNoDefault() {

        var producerLlm = new MockLlmClient()
                .onSend("", endTurn("unknown"));

        var pathALlm = new MockLlmClient()
                .onSend("", endTurn("A result"));

        var pathBLlm = new MockLlmClient()
                .onSend("", endTurn("B result"));

        var registry = new AgentRegistryMemory();
        registry.register(createAgent("producer-agent", producerLlm));
        registry.register(createAgent("path-a-agent", pathALlm));
        registry.register(createAgent("path-b-agent", pathBLlm));

        var runner = new TaskRunner(registry, autoApproveHitl(), new ToolkitRegistry(), new TaskStateStoreMemory(), null, 0, null, new ai.agentican.framework.orchestration.code.CodeStepRegistry());

        var task = Plan.builder("no-match-branch-task")
                .step("decide", "producer-agent", "Pick a path")
                .branch("branch-step", branch -> branch
                        .from("decide")
                        .path("path-a", new PlanStepAgent("do-a", "path-a-agent",
                                "Execute path A", null, false, null, null))
                        .path("path-b", new PlanStepAgent("do-b", "path-b-agent",
                                "Execute path B", null, false, null, null)))
                .build();

        var result = runner.run(task);

        assertEquals(TaskStatus.FAILED, result.status());
    }

    @Test
    void branchSelectsFromJsonArray() {

        var producerLlm = new MockLlmClient()
                .onSend("", endTurn("[\"path-a\"]"));

        var pathALlm = new MockLlmClient()
                .onSend("", endTurn("A from array"));

        var pathBLlm = new MockLlmClient()
                .onSend("", endTurn("B from array"));

        var registry = new AgentRegistryMemory();
        registry.register(createAgent("producer-agent", producerLlm));
        registry.register(createAgent("path-a-agent", pathALlm));
        registry.register(createAgent("path-b-agent", pathBLlm));

        var runner = new TaskRunner(registry, autoApproveHitl(), new ToolkitRegistry(), new TaskStateStoreMemory(), null, 0, null, new ai.agentican.framework.orchestration.code.CodeStepRegistry());

        var task = Plan.builder("json-array-branch-task")
                .step("decide", "producer-agent", "Pick a path")
                .branch("branch-step", branch -> branch
                        .from("decide")
                        .path("path-a", new PlanStepAgent("do-a", "path-a-agent",
                                "Execute path A", null, false, null, null))
                        .path("path-b", new PlanStepAgent("do-b", "path-b-agent",
                                "Execute path B", null, false, null, null)))
                .build();

        var result = runner.run(task);

        assertEquals(TaskStatus.COMPLETED, result.status());
        assertEquals("A from array", result.stepResults().get(1).output());
    }

    @Test
    void branchContainsMatch() {

        var producerLlm = new MockLlmClient()
                .onSend("", endTurn("The answer is path-b because of reasons"));

        var pathALlm = new MockLlmClient()
                .onSend("", endTurn("A result"));

        var pathBLlm = new MockLlmClient()
                .onSend("", endTurn("B via contains"));

        var registry = new AgentRegistryMemory();
        registry.register(createAgent("producer-agent", producerLlm));
        registry.register(createAgent("path-a-agent", pathALlm));
        registry.register(createAgent("path-b-agent", pathBLlm));

        var runner = new TaskRunner(registry, autoApproveHitl(), new ToolkitRegistry(), new TaskStateStoreMemory(), null, 0, null, new ai.agentican.framework.orchestration.code.CodeStepRegistry());

        var task = Plan.builder("contains-branch-task")
                .step("decide", "producer-agent", "Pick a path")
                .branch("branch-step", branch -> branch
                        .from("decide")
                        .path("path-a", new PlanStepAgent("do-a", "path-a-agent",
                                "Execute path A", null, false, null, null))
                        .path("path-b", new PlanStepAgent("do-b", "path-b-agent",
                                "Execute path B", null, false, null, null)))
                .build();

        var result = runner.run(task);

        assertEquals(TaskStatus.COMPLETED, result.status());
        assertEquals("B via contains", result.stepResults().get(1).output());
    }
}
