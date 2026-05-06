package ai.agentican.quarkus.store.jpa;

import ai.agentican.framework.registry.WorkflowRegistry;
import ai.agentican.framework.orchestration.model.WorkflowDefinition;
import ai.agentican.framework.orchestration.model.WorkflowParam;
import ai.agentican.framework.orchestration.model.WorkflowStepAgent;
import ai.agentican.framework.orchestration.model.WorkflowStepBranch;
import ai.agentican.framework.orchestration.model.WorkflowStepLoop;
import ai.agentican.framework.util.Ids;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class JpaWorkflowRegistryTest {

    @Inject
    JpaWorkflowRegistry registry;

    @Inject
    WorkflowRegistry registryInterface;

    @Test
    void interfaceResolvesToJpaBean() {

        assertSame(registry, registryInterface);
    }

    @Test
    void registerRoundTripsSimplePlan() {

        var step = new WorkflowStepAgent("research", "agent-x", "do research",
                List.of(), false, List.of("skill-1"), List.of("tool-a"));

        var plan = WorkflowDefinition.builder("p-" + Ids.generate(), "Research").description("desc")
                .param(new WorkflowParam("topic", null, null, true)).step(step).build();
        registry.register(plan);

        assertSame(plan, registry.byId(plan.id()));
        assertSame(plan, registry.byName("Research"));

        var fresh = new JpaWorkflowRegistry();
        fresh.seed();

        var rehydrated = fresh.byId(plan.id());
        assertNotNull(rehydrated);
        assertEquals("Research", rehydrated.name());
        assertEquals(1, rehydrated.params().size());
        assertEquals("topic", rehydrated.params().getFirst().name());
        assertEquals(1, rehydrated.steps().size());
        assertInstanceOf(WorkflowStepAgent.class, rehydrated.steps().getFirst());

        var rehydratedStep = (WorkflowStepAgent) rehydrated.steps().getFirst();
        assertEquals("agent-x", rehydratedStep.agentName());
        assertEquals(List.of("skill-1"), rehydratedStep.skills());
        assertEquals(List.of("tool-a"), rehydratedStep.tools());
    }

    @Test
    void registerRoundTripsLoopAndBranchPlan() {

        var produce = new WorkflowStepAgent("produce", "finder", "find items",
                List.of(), false, List.of(), List.of());

        var workInLoop = new WorkflowStepAgent("work", "worker", "handle item",
                List.of(), false, List.of(), List.of());

        var loop = new WorkflowStepLoop("loop-step", "produce", List.of(workInLoop), List.of(), false);

        var yesBody = new WorkflowStepAgent("yes-step", "yes-agent", "approved branch",
                List.of(), false, List.of(), List.of());
        var noBody = new WorkflowStepAgent("no-step", "no-agent", "rejected branch",
                List.of(), false, List.of(), List.of());

        var branch = new WorkflowStepBranch("branch-step", "work",
                List.of(new WorkflowStepBranch.Path("yes", List.of(yesBody)),
                        new WorkflowStepBranch.Path("no", List.of(noBody))),
                "no", List.of(), false);

        var plan = WorkflowDefinition.builder("p-" + Ids.generate(), "LoopAndBranch").description("shape test")
                .steps(List.of(produce, loop, branch))
                .build();

        registry.register(plan);

        var fresh = new JpaWorkflowRegistry();
        fresh.seed();

        var rehydrated = fresh.byId(plan.id());
        assertNotNull(rehydrated);
        assertEquals(3, rehydrated.steps().size());
        assertInstanceOf(WorkflowStepLoop.class, rehydrated.steps().get(1));
        assertInstanceOf(WorkflowStepBranch.class, rehydrated.steps().get(2));

        var rehydratedLoop = (WorkflowStepLoop) rehydrated.steps().get(1);
        assertEquals("produce", rehydratedLoop.over());
        assertEquals(1, rehydratedLoop.body().size());

        var rehydratedBranch = (WorkflowStepBranch) rehydrated.steps().get(2);
        assertEquals("work", rehydratedBranch.from());
        assertEquals(2, rehydratedBranch.paths().size());
        assertEquals("no", rehydratedBranch.defaultPath());
    }

    @Test
    void registerIfAbsentDoesNotOverwrite() {

        var first = makePlan("RaceCondition", "first-" + Ids.generate(), "first");
        var second = WorkflowDefinition.builder(first.id(), "RaceCondition").description("second")
                .steps(first.steps())
                .build();

        registry.registerIfAbsent(first);
        var returned = registry.registerIfAbsent(second);
        assertEquals("first", returned.description());
    }

    private static WorkflowDefinition makePlan(String name, String id, String description) {

        var step = new WorkflowStepAgent("s1", "a1", "do work",
                List.of(), false, List.of(), List.of());

        return WorkflowDefinition.builder(id, name).description(description).step(step).build();
    }
}
