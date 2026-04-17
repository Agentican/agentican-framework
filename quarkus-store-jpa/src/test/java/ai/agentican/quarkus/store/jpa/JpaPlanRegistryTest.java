package ai.agentican.quarkus.store.jpa;

import ai.agentican.framework.orchestration.PlanRegistry;
import ai.agentican.framework.orchestration.model.Plan;
import ai.agentican.framework.orchestration.model.PlanParam;
import ai.agentican.framework.orchestration.model.PlanStepAgent;
import ai.agentican.framework.orchestration.model.PlanStepBranch;
import ai.agentican.framework.orchestration.model.PlanStepLoop;
import ai.agentican.framework.util.Ids;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class JpaPlanRegistryTest {

    @Inject
    JpaPlanRegistry registry;

    @Inject
    PlanRegistry registryInterface;

    @Test
    void interfaceResolvesToJpaBean() {

        assertSame(registry, registryInterface);
    }

    @Test
    void registerRoundTripsSimplePlan() {

        var step = PlanStepAgent.of("research", "agent-x", "do research",
                List.of(), false, List.of("skill-1"), List.of("tool-a"));

        var plan = new Plan("p-" + Ids.generate(), "Research", "desc", List.of(new PlanParam("topic")), List.of(step));
        registry.register(plan);

        assertSame(plan, registry.getById(plan.id()));
        assertSame(plan, registry.get("Research"));

        var fresh = new JpaPlanRegistry();
        fresh.seed();

        var rehydrated = fresh.getById(plan.id());
        assertNotNull(rehydrated);
        assertEquals("Research", rehydrated.name());
        assertEquals(1, rehydrated.params().size());
        assertEquals("topic", rehydrated.params().getFirst().name());
        assertEquals(1, rehydrated.steps().size());
        assertInstanceOf(PlanStepAgent.class, rehydrated.steps().getFirst());

        var rehydratedStep = (PlanStepAgent) rehydrated.steps().getFirst();
        assertEquals("agent-x", rehydratedStep.agentId());
        assertEquals(List.of("skill-1"), rehydratedStep.skills());
        assertEquals(List.of("tool-a"), rehydratedStep.tools());
    }

    @Test
    void registerRoundTripsLoopAndBranchPlan() {

        var produce = PlanStepAgent.of("produce", "finder", "find items",
                List.of(), false, List.of(), List.of());

        var workInLoop = PlanStepAgent.of("work", "worker", "handle item",
                List.of(), false, List.of(), List.of());

        var loop = new PlanStepLoop("loop-step", "produce", List.of(workInLoop), List.of(), false);

        var yesBody = PlanStepAgent.of("yes-step", "yes-agent", "approved branch",
                List.of(), false, List.of(), List.of());
        var noBody = PlanStepAgent.of("no-step", "no-agent", "rejected branch",
                List.of(), false, List.of(), List.of());

        var branch = new PlanStepBranch("branch-step", "work",
                List.of(new PlanStepBranch.Path("yes", List.of(yesBody)),
                        new PlanStepBranch.Path("no", List.of(noBody))),
                "no", List.of(), false);

        var plan = new Plan("p-" + Ids.generate(), "LoopAndBranch", "shape test",
                List.of(), List.of(produce, loop, branch));

        registry.register(plan);

        var fresh = new JpaPlanRegistry();
        fresh.seed();

        var rehydrated = fresh.getById(plan.id());
        assertNotNull(rehydrated);
        assertEquals(3, rehydrated.steps().size());
        assertInstanceOf(PlanStepLoop.class, rehydrated.steps().get(1));
        assertInstanceOf(PlanStepBranch.class, rehydrated.steps().get(2));

        var rehydratedLoop = (PlanStepLoop) rehydrated.steps().get(1);
        assertEquals("produce", rehydratedLoop.over());
        assertEquals(1, rehydratedLoop.body().size());

        var rehydratedBranch = (PlanStepBranch) rehydrated.steps().get(2);
        assertEquals("work", rehydratedBranch.from());
        assertEquals(2, rehydratedBranch.paths().size());
        assertEquals("no", rehydratedBranch.defaultPath());
    }

    @Test
    void registerIfAbsentDoesNotOverwrite() {

        var first = makePlan("RaceCondition", "first-" + Ids.generate(), "first");
        var second = new Plan(first.id(), "RaceCondition", "second",
                List.of(), first.steps());

        registry.registerIfAbsent(first);
        var returned = registry.registerIfAbsent(second);
        assertEquals("first", returned.description());
    }

    private static Plan makePlan(String name, String id, String description) {

        var step = PlanStepAgent.of("s1", "a1", "do work",
                List.of(), false, List.of(), List.of());

        return new Plan(id, name, description, List.of(), List.of(step));
    }
}
