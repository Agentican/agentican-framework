package ai.agentican.quarkus.rest;

import ai.agentican.framework.hitl.HitlCheckpoint;
import ai.agentican.framework.state.TaskStateStore;
import ai.agentican.quarkus.event.*;
import ai.agentican.quarkus.rest.sse.EventTimeline;
import ai.agentican.quarkus.rest.sse.SequencedEvent;
import io.smallrye.mutiny.Multi;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;

@ApplicationScoped
public class TaskEventBus {

    private static final int DEFAULT_BUFFER_CAPACITY = 100;

    @Inject TaskStateStore taskStateStore;

    private final ConcurrentMap<String, EventTimeline> timelines = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, List<HitlCheckpoint>> pendingCheckpoints = new ConcurrentHashMap<>();

    public void onPlanStarted(@Observes PlanStartedEvent event) {

        emitAndBubble(event.taskId(), event);
    }

    public void onPlanCompleted(@Observes PlanCompletedEvent event) {

        emitAndBubble(event.taskId(), event);
    }

    public void onTaskStarted(@Observes TaskStartedEvent event) {

        timelineFor(event.taskId()).emit(event);
    }

    public void onTaskCompleted(@Observes TaskCompletedEvent event) {

        var timeline = timelines.remove(event.taskId());

        if (timeline != null)
            timeline.complete(event);

        pendingCheckpoints.remove(event.taskId());
    }

    public void onIterationStarted(@Observes IterationStartedEvent event) {

        emitAndBubble(event.parentTaskId(), event);
    }

    public void onIterationCompleted(@Observes IterationCompletedEvent event) {

        emitAndBubble(event.parentTaskId(), event);
    }

    public void onStepStarted(@Observes StepStartedEvent event) {

        emitAndBubble(event.taskId(), event);
    }

    public void onStepCompleted(@Observes StepCompletedEvent event) {

        emitAndBubble(event.taskId(), event);
    }

    public void onRunStarted(@Observes RunStartedEvent event) {

        emitAndBubble(event.taskId(), event);
    }

    public void onRunCompleted(@Observes RunCompletedEvent event) {

        emitAndBubble(event.taskId(), event);
    }

    public void onTurnStarted(@Observes TurnStartedEvent event) {

        emitAndBubble(event.taskId(), event);
    }

    public void onTurnCompleted(@Observes TurnCompletedEvent event) {

        emitAndBubble(event.taskId(), event);
    }

    public void onMessageSent(@Observes MessageSentEvent event) {

        emitAndBubble(event.taskId(), event);
    }

    public void onResponseReceived(@Observes ResponseReceivedEvent event) {

        emitAndBubble(event.taskId(), event);
    }

    public void onToolCallStarted(@Observes ToolCallStartedEvent event) {

        emitAndBubble(event.taskId(), event);
    }

    public void onToolCallCompleted(@Observes ToolCallCompletedEvent event) {

        emitAndBubble(event.taskId(), event);
    }

    public void onHitlCheckpoint(@Observes HitlCheckpointEvent event) {

        pendingCheckpoints
                .computeIfAbsent(event.taskId(), id -> new CopyOnWriteArrayList<>())
                .add(event.checkpoint());

        emitAndBubble(event.taskId(), event);
    }

    private void emitAndBubble(String taskId, Object event) {

        if (taskId == null) return;

        timelineFor(taskId).emit(event);

        var taskLog = taskStateStore != null ? taskStateStore.load(taskId) : null;
        var guard = 0;

        while (taskLog != null && taskLog.parentTaskId() != null && guard++ < 32) {

            timelineFor(taskLog.parentTaskId()).emit(event);
            taskLog = taskStateStore.load(taskLog.parentTaskId());
        }
    }

    public Multi<SequencedEvent> stream(String taskId, long lastEventId) {

        return timelineFor(taskId).stream(lastEventId);
    }

    public Multi<SequencedEvent> stream(String taskId) {

        return stream(taskId, -1);
    }

    public void clearCheckpoint(String checkpointId) {

        pendingCheckpoints.values().forEach(list ->
                list.removeIf(c -> c.id().equals(checkpointId)));
    }

    public List<HitlCheckpoint> pendingFor(String taskId) {

        return List.copyOf(pendingCheckpoints.getOrDefault(taskId, List.of()));
    }

    public Map<String, List<HitlCheckpoint>> allPending() {

        return Map.copyOf(pendingCheckpoints);
    }

    @PreDestroy
    void shutdown() {

        timelines.values().forEach(EventTimeline::complete);
        timelines.clear();
        pendingCheckpoints.clear();
    }

    private EventTimeline timelineFor(String taskId) {

        return timelines.computeIfAbsent(taskId, id -> new EventTimeline(DEFAULT_BUFFER_CAPACITY));
    }
}
