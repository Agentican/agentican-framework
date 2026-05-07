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

        return Agent.builder().config(AgentConfig.builder().name(name).id(name).role("Test agent for " + name).build()).runner(runner).build();
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

        var runner = new WorkflowRunner(registry, autoApproveHitl(), new ToolkitRegistry(), new WorkflowRunStoreMemory(), null, 0, null, new ai.agentican.framework.orchestration.code.CodeStepRegistry());

        var task = WorkflowDefinition.builder("branch-task", "branch-task")
                .step().name("decide").agent("producer-agent").instructions("Pick a path").end()
                .branch()
                        .name("branch-step")
                        .from("decide")
                        .branch()
                                .name("path-a")
                                .step().name("do-a").agent("path-a-agent").instructions("Execute path A").end()
                                .end()
                        .branch()
                                .name("path-b")
                                .step().name("do-b").agent("path-b-agent").instructions("Execute path B").end()
                                .end()
                        .end()
                .build();

        var result = runner.run(task);

        assertEquals(WorkflowRunStatus.COMPLETED, result.status());
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

        var runner = new WorkflowRunner(registry, autoApproveHitl(), new ToolkitRegistry(), new WorkflowRunStoreMemory(), null, 0, null, new ai.agentican.framework.orchestration.code.CodeStepRegistry());

        var task = WorkflowDefinition.builder("default-branch-task", "default-branch-task")
                .step().name("decide").agent("producer-agent").instructions("Pick a path").end()
                .branch()
                        .name("branch-step")
                        .from("decide")
                        .defaultBranch("path-b")
                        .branch()
                                .name("path-a")
                                .step().name("do-a").agent("path-a-agent").instructions("Execute path A").end()
                                .end()
                        .branch()
                                .name("path-b")
                                .step().name("do-b").agent("path-b-agent").instructions("Execute path B").end()
                                .end()
                        .end()
                .build();

        var result = runner.run(task);

        assertEquals(WorkflowRunStatus.COMPLETED, result.status());
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

        var runner = new WorkflowRunner(registry, autoApproveHitl(), new ToolkitRegistry(), new WorkflowRunStoreMemory(), null, 0, null, new ai.agentican.framework.orchestration.code.CodeStepRegistry());

        var task = WorkflowDefinition.builder("no-match-branch-task", "no-match-branch-task")
                .step().name("decide").agent("producer-agent").instructions("Pick a path").end()
                .branch()
                        .name("branch-step")
                        .from("decide")
                        .branch()
                                .name("path-a")
                                .step().name("do-a").agent("path-a-agent").instructions("Execute path A").end()
                                .end()
                        .branch()
                                .name("path-b")
                                .step().name("do-b").agent("path-b-agent").instructions("Execute path B").end()
                                .end()
                        .end()
                .build();

        var result = runner.run(task);

        assertEquals(WorkflowRunStatus.FAILED, result.status());
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

        var runner = new WorkflowRunner(registry, autoApproveHitl(), new ToolkitRegistry(), new WorkflowRunStoreMemory(), null, 0, null, new ai.agentican.framework.orchestration.code.CodeStepRegistry());

        var task = WorkflowDefinition.builder("json-array-branch-task", "json-array-branch-task")
                .step().name("decide").agent("producer-agent").instructions("Pick a path").end()
                .branch()
                        .name("branch-step")
                        .from("decide")
                        .branch()
                                .name("path-a")
                                .step().name("do-a").agent("path-a-agent").instructions("Execute path A").end()
                                .end()
                        .branch()
                                .name("path-b")
                                .step().name("do-b").agent("path-b-agent").instructions("Execute path B").end()
                                .end()
                        .end()
                .build();

        var result = runner.run(task);

        assertEquals(WorkflowRunStatus.COMPLETED, result.status());
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

        var runner = new WorkflowRunner(registry, autoApproveHitl(), new ToolkitRegistry(), new WorkflowRunStoreMemory(), null, 0, null, new ai.agentican.framework.orchestration.code.CodeStepRegistry());

        var task = WorkflowDefinition.builder("contains-branch-task", "contains-branch-task")
                .step().name("decide").agent("producer-agent").instructions("Pick a path").end()
                .branch()
                        .name("branch-step")
                        .from("decide")
                        .branch()
                                .name("path-a")
                                .step().name("do-a").agent("path-a-agent").instructions("Execute path A").end()
                                .end()
                        .branch()
                                .name("path-b")
                                .step().name("do-b").agent("path-b-agent").instructions("Execute path B").end()
                                .end()
                        .end()
                .build();

        var result = runner.run(task);

        assertEquals(WorkflowRunStatus.COMPLETED, result.status());
        assertEquals("B via contains", result.stepResults().get(1).output());
    }
}
