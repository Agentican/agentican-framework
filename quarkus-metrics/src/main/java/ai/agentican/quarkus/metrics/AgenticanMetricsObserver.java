package ai.agentican.quarkus.metrics;

import ai.agentican.quarkus.event.HitlCheckpointEvent;
import ai.agentican.quarkus.event.StepCompletedEvent;
import ai.agentican.quarkus.event.TaskCompletedEvent;
import ai.agentican.quarkus.event.TaskReapedEvent;
import ai.agentican.quarkus.event.TaskResumedEvent;
import ai.agentican.quarkus.event.TaskStartedEvent;

import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.runtime.Startup;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Startup
@ApplicationScoped
public class AgenticanMetricsObserver {

    @Inject
    MeterRegistry registry;

    private final AtomicLong activeTasks = new AtomicLong();
    private final AtomicLong pendingCheckpoints = new AtomicLong();
    private final Set<String> tasksWithPendingCheckpoints = ConcurrentHashMap.newKeySet();
    private final ConcurrentHashMap<String, Instant> taskStartTimes = new ConcurrentHashMap<>();
    private final Set<String> resumedTasks = ConcurrentHashMap.newKeySet();

    @PostConstruct
    void registerGauges() {

        registry.gauge("agentican.tasks.active", activeTasks);
        registry.gauge("agentican.hitl.checkpoints.pending", pendingCheckpoints);
    }

    void onTaskStarted(@Observes TaskStartedEvent event) {

        activeTasks.incrementAndGet();
        taskStartTimes.put(event.taskId(), Instant.now());
    }

    void onTaskCompleted(@Observes TaskCompletedEvent event) {

        activeTasks.decrementAndGet();

        var status = event.status().name();

        registry.counter("agentican.tasks.completed", "status", status).increment();

        var startTime = taskStartTimes.remove(event.taskId());

        if (startTime != null) {

            var duration = Duration.between(startTime, Instant.now());
            registry.timer("agentican.tasks.duration", "status", status).record(duration);
        }

        if (tasksWithPendingCheckpoints.remove(event.taskId()))
            pendingCheckpoints.decrementAndGet();

        if (resumedTasks.remove(event.taskId()))
            registry.counter("agentican.tasks.resume.outcome", "outcome", status).increment();
    }

    void onStepCompleted(@Observes StepCompletedEvent event) {

        registry.counter("agentican.steps.completed", "status", event.status().name()).increment();
    }

    void onHitlCheckpoint(@Observes HitlCheckpointEvent event) {

        registry.counter("agentican.hitl.checkpoints.created", "type",
                event.checkpoint().type().name()).increment();

        if (tasksWithPendingCheckpoints.add(event.taskId()))
            pendingCheckpoints.incrementAndGet();
    }

    void onTaskResumed(@Observes TaskResumedEvent event) {

        registry.counter("agentican.tasks.resumed").increment();
        resumedTasks.add(event.taskId());
    }

    void onTaskReaped(@Observes TaskReapedEvent event) {

        var reason = event.reason() != null ? event.reason().name() : "UNKNOWN";
        registry.counter("agentican.tasks.reaped", "reason", reason).increment();
    }
}
