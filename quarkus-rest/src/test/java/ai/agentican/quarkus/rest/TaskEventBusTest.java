package ai.agentican.quarkus.rest;

import ai.agentican.framework.hitl.HitlCheckpoint;
import ai.agentican.framework.state.TaskLog;
import ai.agentican.framework.orchestration.model.Plan;
import ai.agentican.framework.orchestration.execution.TaskStatus;
import ai.agentican.quarkus.event.HitlCheckpointEvent;
import ai.agentican.quarkus.event.StepCompletedEvent;
import ai.agentican.quarkus.event.TaskCompletedEvent;
import ai.agentican.quarkus.event.TaskStartedEvent;
import ai.agentican.quarkus.rest.sse.SequencedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TaskEventBusTest {

    private TaskEventBus bus;

    @BeforeEach
    void setUp() {

        bus = new TaskEventBus();
    }

    @Test
    void streamReceivesEventsForOneTaskOnly() {

        var collectedT1 = new CopyOnWriteArrayList<SequencedEvent>();
        var collectedT2 = new CopyOnWriteArrayList<SequencedEvent>();

        bus.stream("t1").subscribe().with(collectedT1::add);
        bus.stream("t2").subscribe().with(collectedT2::add);

        var t1Log = new TaskLog("t1", "demo", task("demo"), Map.of());
        var t2Log = new TaskLog("t2", "demo", task("demo"), Map.of());

        bus.onTaskStarted(new TaskStartedEvent("t1", "demo", t1Log));
        bus.onTaskStarted(new TaskStartedEvent("t2", "demo", t2Log));

        bus.onStepCompleted(new StepCompletedEvent(null, "t1", "step-a", TaskStatus.COMPLETED));

        bus.onTaskCompleted(new TaskCompletedEvent("t1", "demo", TaskStatus.COMPLETED, t1Log));
        bus.onTaskCompleted(new TaskCompletedEvent("t2", "demo", TaskStatus.FAILED, t2Log));

        assertEquals(3, collectedT1.size());

        assertEquals(2, collectedT2.size());
    }

    @Test
    void hitlCheckpointEventTracksPendingByTask() {

        var checkpoint = new HitlCheckpoint(
                "ck-1", HitlCheckpoint.Type.STEP_OUTPUT, "step-a", "Approve?", "...");

        bus.onHitlCheckpoint(new HitlCheckpointEvent("t1", "step-id-a", "step-a", checkpoint));

        assertEquals(1, bus.pendingFor("t1").size());
        assertEquals("ck-1", bus.pendingFor("t1").getFirst().id());
        assertEquals(0, bus.pendingFor("t-other").size());
    }

    @Test
    void clearCheckpointRemovesFromPending() {

        var checkpoint = new HitlCheckpoint(
                "ck-1", HitlCheckpoint.Type.QUESTION, "step-a", "Q?", "...");

        bus.onHitlCheckpoint(new HitlCheckpointEvent("t1", "step-id-a", "step-a", checkpoint));
        assertEquals(1, bus.pendingFor("t1").size());

        bus.clearCheckpoint("ck-1");
        assertEquals(0, bus.pendingFor("t1").size());
    }

    @Test
    void taskCompletedEventClearsPendingAndCompletesStream() {

        var collected = new ArrayList<SequencedEvent>();
        var completed = new boolean[] { false };

        bus.stream("t1").subscribe().with(collected::add, t -> {}, () -> completed[0] = true);

        bus.onHitlCheckpoint(new HitlCheckpointEvent("t1", "sid", "s",
                new HitlCheckpoint("ck-1", HitlCheckpoint.Type.STEP_OUTPUT, "s", "d", "c")));

        bus.onTaskCompleted(new TaskCompletedEvent("t1", "demo", TaskStatus.COMPLETED,
                new TaskLog("t1", "demo", task("demo"), Map.of())));

        assertEquals(0, bus.pendingFor("t1").size());
        assertTrue(completed[0], "Stream should complete on TaskCompletedEvent");
    }

    @Test
    void allPendingReturnsAllTasksWithCheckpoints() {

        bus.onHitlCheckpoint(new HitlCheckpointEvent("t1", "sid", "s",
                new HitlCheckpoint("ck-1", HitlCheckpoint.Type.STEP_OUTPUT, "s", "d", "c")));
        bus.onHitlCheckpoint(new HitlCheckpointEvent("t2", "sid", "s",
                new HitlCheckpoint("ck-2", HitlCheckpoint.Type.STEP_OUTPUT, "s", "d", "c")));

        var all = bus.allPending();

        assertEquals(2, all.size());
        assertTrue(all.containsKey("t1"));
        assertTrue(all.containsKey("t2"));
    }

    @Test
    void replayBufferDeliversMissedEventsOnReconnect() {

        var log = new TaskLog("t1", "demo", task("demo"), Map.of());

        bus.onTaskStarted(new TaskStartedEvent("t1", "demo", log));
        bus.onStepCompleted(new StepCompletedEvent(null, "t1", "s", TaskStatus.COMPLETED));

        var replayed = new CopyOnWriteArrayList<SequencedEvent>();
        bus.stream("t1", -1).subscribe().with(replayed::add);

        assertEquals(2, replayed.size());
        assertEquals(0, replayed.get(0).id());
        assertEquals(1, replayed.get(1).id());

        bus.onStepCompleted(new StepCompletedEvent(null, "t1", "s2", TaskStatus.COMPLETED));
        assertEquals(3, replayed.size());
        assertEquals(2, replayed.get(2).id());
    }

    @Test
    void replayFromMiddleSkipsEarlierEvents() {

        var log = new TaskLog("t1", "demo", task("demo"), Map.of());

        bus.onTaskStarted(new TaskStartedEvent("t1", "demo", log));
        bus.onStepCompleted(new StepCompletedEvent(null, "t1", "s", TaskStatus.COMPLETED));
        bus.onStepCompleted(new StepCompletedEvent(null, "t1", "s2", TaskStatus.COMPLETED));

        var replayed = new CopyOnWriteArrayList<SequencedEvent>();
        bus.stream("t1", 1).subscribe().with(replayed::add);

        assertEquals(1, replayed.size());
        assertEquals(2, replayed.get(0).id());
    }

    @Test
    void completedTaskReplaysThenCompletes() {

        var log = new TaskLog("t1", "demo", task("demo"), Map.of());

        bus.onTaskStarted(new TaskStartedEvent("t1", "demo", log));
        bus.onTaskCompleted(new TaskCompletedEvent("t1", "demo", TaskStatus.COMPLETED, log));

        var replayed = new CopyOnWriteArrayList<SequencedEvent>();
        var completed = new boolean[] { false };

        bus.stream("t1", -1).subscribe().with(replayed::add, t -> {}, () -> completed[0] = true);

        assertEquals(0, replayed.size());
    }

    private static Plan task(String name) {

        return Plan.builder(name).description("d").step("s", "a", "i").build();
    }
}
