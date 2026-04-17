package ai.agentican.quarkus.event;

import jakarta.enterprise.event.Observes;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.List;

@Singleton
public class EventCollector {

    public final List<TaskStartedEvent> taskStarted = new ArrayList<>();
    public final List<TaskCompletedEvent> taskCompleted = new ArrayList<>();
    public final List<StepCompletedEvent> stepCompleted = new ArrayList<>();
    public final List<HitlCheckpointEvent> hitlCheckpoints = new ArrayList<>();

    public void onTaskStarted(@Observes TaskStartedEvent event) { taskStarted.add(event); }
    public void onTaskCompleted(@Observes TaskCompletedEvent event) { taskCompleted.add(event); }
    public void onStepCompleted(@Observes StepCompletedEvent event) { stepCompleted.add(event); }
    public void onHitlCheckpoint(@Observes HitlCheckpointEvent event) { hitlCheckpoints.add(event); }

    public void reset() {

        taskStarted.clear();
        taskCompleted.clear();
        stepCompleted.clear();
        hitlCheckpoints.clear();
    }
}
