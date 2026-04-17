package ai.agentican.quarkus.rest.sse;

import ai.agentican.framework.hitl.HitlCheckpoint;
import ai.agentican.framework.hitl.HitlCheckpointType;
import ai.agentican.framework.state.TaskLog;
import ai.agentican.framework.orchestration.model.Plan;
import ai.agentican.framework.orchestration.execution.TaskStatus;
import ai.agentican.quarkus.event.HitlCheckpointEvent;
import ai.agentican.quarkus.event.StepCompletedEvent;
import ai.agentican.quarkus.event.TaskCompletedEvent;
import ai.agentican.quarkus.event.TaskStartedEvent;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SseEventTypesTest {

    @Test
    void mapsEachLifecycleEventToWireName() {

        var task = Plan.builder("demo").description("d").step("s", "a", "i").build();
        var log = new TaskLog("t1", "demo", task, Map.of());

        assertEquals(SseEventTypes.TASK_STARTED,
                SseEventTypes.nameFor(new TaskStartedEvent("t1", "demo", log)));

        assertEquals(SseEventTypes.STEP_COMPLETED,
                SseEventTypes.nameFor(new StepCompletedEvent(null, "t1", "s", TaskStatus.COMPLETED)));

        assertEquals(SseEventTypes.HITL_CHECKPOINT,
                SseEventTypes.nameFor(new HitlCheckpointEvent("t1", "sid", "s",
                        new HitlCheckpoint("ck", HitlCheckpointType.STEP_OUTPUT, "s", "d", "c"))));

        assertEquals(SseEventTypes.TASK_COMPLETED,
                SseEventTypes.nameFor(new TaskCompletedEvent("t1", "demo", TaskStatus.COMPLETED, log)));
    }

    @Test
    void unknownAndNullFallBackToGenericName() {

        assertEquals(SseEventTypes.UNKNOWN, SseEventTypes.nameFor(null));
        assertEquals(SseEventTypes.UNKNOWN, SseEventTypes.nameFor("plain string"));
    }
}
