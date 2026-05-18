package ai.agentican.framework.event;

import ai.agentican.framework.orchestration.execution.WorkflowRunStatus;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AgenticanEventBusTest {

    private final AgenticanEvent sample = new TaskCompleted("task-1", WorkflowRunStatus.COMPLETED);

    @Test
    void publishWithNoListenersIsNoOp() {

        var bus = new AgenticanEventBus();

        assertDoesNotThrow(() -> bus.publish(sample));
        assertEquals(0, bus.subscriberCount());
    }

    @Test
    void subscribeFanOutsToAllObservers() {

        var bus = new AgenticanEventBus();
        var seenA = new ArrayList<AgenticanEvent>();
        var seenB = new ArrayList<AgenticanEvent>();

        bus.subscribe(seenA::add);
        bus.subscribe(seenB::add);

        bus.publish(sample);

        assertEquals(List.of(sample), seenA);
        assertEquals(List.of(sample), seenB);
    }

    @Test
    void subscribeFirstListenersRunBeforeObservers() {

        var bus = new AgenticanEventBus();
        var order = new ArrayList<String>();

        bus.subscribe(e -> order.add("observer-1"));
        bus.subscribeFirst(e -> order.add("first-tier-1"));
        bus.subscribe(e -> order.add("observer-2"));
        bus.subscribeFirst(e -> order.add("first-tier-2"));

        bus.publish(sample);

        // First-tier listeners run in registration order, before any observer.
        assertEquals(List.of("first-tier-1", "first-tier-2", "observer-1", "observer-2"), order);
    }

    @Test
    void observerExceptionIsSwallowedAndOthersStillFire() {

        var bus = new AgenticanEventBus();
        var afterThrower = new ArrayList<AgenticanEvent>();

        bus.subscribe(e -> { throw new RuntimeException("observer-1 boom"); });
        bus.subscribe(afterThrower::add);

        // Publish must not propagate the observer's exception.
        assertDoesNotThrow(() -> bus.publish(sample));

        // The downstream observer still got the event.
        assertEquals(List.of(sample), afterThrower);
    }

    @Test
    void firstTierExceptionPropagates() {

        var bus = new AgenticanEventBus();
        var afterThrower = new ArrayList<AgenticanEvent>();

        bus.subscribeFirst(e -> { throw new RuntimeException("persister boom"); });
        bus.subscribe(afterThrower::add);

        var ex = assertThrows(RuntimeException.class, () -> bus.publish(sample));

        assertEquals("persister boom", ex.getMessage());

        // Persister failure short-circuits — observers downstream MUST NOT have fired.
        assertTrue(afterThrower.isEmpty(),
                "downstream observers should not fire when the persister throws");
    }

    @Test
    void publishOrderAcrossEventsIsPerListenerFifo() {

        var bus = new AgenticanEventBus();
        var seen = new ArrayList<String>();

        bus.subscribe(e -> seen.add(e.taskId()));

        bus.publish(new TaskCompleted("a", WorkflowRunStatus.COMPLETED));
        bus.publish(new TaskCompleted("b", WorkflowRunStatus.COMPLETED));
        bus.publish(new TaskCompleted("c", WorkflowRunStatus.COMPLETED));

        assertEquals(List.of("a", "b", "c"), seen);
    }

    @Test
    void subscriberCountReflectsAllTiers() {

        var bus = new AgenticanEventBus();

        bus.subscribeFirst(e -> { });
        bus.subscribe(e -> { });
        bus.subscribe(e -> { });

        assertEquals(3, bus.subscriberCount());
        assertEquals(3, bus.listeners().size());
    }

    @Test
    void nullEventOrListenerRejected() {

        var bus = new AgenticanEventBus();

        assertThrows(IllegalArgumentException.class, () -> bus.publish(null));
        assertThrows(IllegalArgumentException.class, () -> bus.subscribe(null));
        assertThrows(IllegalArgumentException.class, () -> bus.subscribeFirst(null));
    }
}
