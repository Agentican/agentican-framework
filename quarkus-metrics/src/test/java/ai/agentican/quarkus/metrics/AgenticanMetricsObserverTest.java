package ai.agentican.quarkus.metrics;

import ai.agentican.framework.hitl.HitlCheckpoint;
import ai.agentican.framework.state.TaskLog;
import ai.agentican.framework.orchestration.model.Plan;
import ai.agentican.framework.orchestration.execution.TaskStatus;
import ai.agentican.quarkus.event.HitlCheckpointEvent;
import ai.agentican.quarkus.event.StepCompletedEvent;
import ai.agentican.quarkus.event.TaskCompletedEvent;
import ai.agentican.quarkus.event.TaskStartedEvent;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AgenticanMetricsObserverTest {

    private SimpleMeterRegistry registry;
    private AgenticanMetricsObserver observer;

    @BeforeEach
    void setUp() {

        registry = new SimpleMeterRegistry();
        observer = new AgenticanMetricsObserver();
        observer.registry = registry;
        observer.registerGauges();
    }

    @Test
    void taskStartedIncrementsActiveGauge() {

        observer.onTaskStarted(started("t1"));
        observer.onTaskStarted(started("t2"));

        assertEquals(2.0, registry.get("agentican.tasks.active").gauge().value());
    }

    @Test
    void taskCompletedDecrementsActiveAndRecordsCounter() {

        observer.onTaskStarted(started("t1"));
        observer.onTaskCompleted(completed("t1", TaskStatus.COMPLETED));

        assertEquals(0.0, registry.get("agentican.tasks.active").gauge().value());
        assertEquals(1.0, registry.counter("agentican.tasks.completed", "status", "COMPLETED").count());
    }

    @Test
    void taskCompletedRecordsDuration() {

        observer.onTaskStarted(started("t1"));
        observer.onTaskCompleted(completed("t1", TaskStatus.COMPLETED));

        var timer = registry.timer("agentican.tasks.duration", "status", "COMPLETED");
        assertEquals(1, timer.count());
    }

    @Test
    void taskFailedTaggedSeparatelyFromCompleted() {

        observer.onTaskStarted(started("t1"));
        observer.onTaskStarted(started("t2"));
        observer.onTaskCompleted(completed("t1", TaskStatus.COMPLETED));
        observer.onTaskCompleted(completed("t2", TaskStatus.FAILED));

        assertEquals(1.0, registry.counter("agentican.tasks.completed", "status", "COMPLETED").count());
        assertEquals(1.0, registry.counter("agentican.tasks.completed", "status", "FAILED").count());
    }

    @Test
    void stepCompletedRecordsCounter() {

        observer.onStepCompleted(new StepCompletedEvent(null, "t1", "s1", TaskStatus.COMPLETED));
        observer.onStepCompleted(new StepCompletedEvent(null, "t1", "s2", TaskStatus.FAILED));

        assertEquals(1.0, registry.counter("agentican.steps.completed", "status", "COMPLETED").count());
        assertEquals(1.0, registry.counter("agentican.steps.completed", "status", "FAILED").count());
    }

    @Test
    void hitlCheckpointRecordsCounterAndPendingGauge() {

        var checkpoint = new HitlCheckpoint("ck-1", HitlCheckpoint.Type.TOOL_CALL, "s1", "d", "c");
        observer.onHitlCheckpoint(new HitlCheckpointEvent("t1", "step-1", "s1", checkpoint));

        assertEquals(1.0, registry.counter("agentican.hitl.checkpoints.created", "type", "TOOL_CALL").count());
        assertEquals(1.0, registry.get("agentican.hitl.checkpoints.pending").gauge().value());
    }

    @Test
    void taskCompletedClearsPendingCheckpointGauge() {

        var checkpoint = new HitlCheckpoint("ck-1", HitlCheckpoint.Type.STEP_OUTPUT, "s1", "d", "c");
        observer.onHitlCheckpoint(new HitlCheckpointEvent("t1", "step-1", "s1", checkpoint));

        assertEquals(1.0, registry.get("agentican.hitl.checkpoints.pending").gauge().value());

        observer.onTaskCompleted(completed("t1", TaskStatus.COMPLETED));

        assertEquals(0.0, registry.get("agentican.hitl.checkpoints.pending").gauge().value());
    }

    private static TaskStartedEvent started(String taskId) {

        return new TaskStartedEvent(taskId, "demo", newLog(taskId));
    }

    private static TaskCompletedEvent completed(String taskId, TaskStatus status) {

        return new TaskCompletedEvent(taskId, "demo", status, newLog(taskId));
    }

    private static TaskLog newLog(String taskId) {

        return new TaskLog(taskId, "demo",
                Plan.builder("demo").description("d").step("s", "a", "i").build(), Map.of());
    }
}
