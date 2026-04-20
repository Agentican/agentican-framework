package ai.agentican.quarkus.otel;

import ai.agentican.framework.agent.AgentStatus;
import ai.agentican.framework.orchestration.execution.TaskStatus;
import ai.agentican.framework.store.TaskStateStoreMemory;

import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TracedLifecycleListenerSubTaskTest {

    @Test
    void subTaskSpanIsParentedToDispatchingStepSpan() {

        var exporter = new InMemorySpanExporter();
        var provider = SdkTracerProvider.builder()
                .addSpanProcessor(SimpleSpanProcessor.create(exporter))
                .build();
        var tracer = provider.get("test");

        var store = new TaskStateStoreMemory();
        var listener = new TracedLifecycleListener(tracer, store);

        var topTask = "task-top";
        var loopStep = "step-loop";
        store.taskStarted(topTask, "top", null, Map.of());
        listener.onTaskStarted(topTask);
        store.stepStarted(topTask, loopStep, "loop-step");
        listener.onStepStarted(topTask, loopStep);

        var subTask = "task-iter-1";
        var subStep = "step-inner";
        var subRun = "run-inner";
        store.taskStarted(subTask, "iter-1", null, Map.of(), topTask, loopStep, 0);
        listener.onTaskStarted(subTask);
        store.stepStarted(subTask, subStep, "inner-step");
        listener.onStepStarted(subTask, subStep);
        store.runStarted(subTask, subStep, subRun, "inner-agent");
        listener.onRunStarted(subTask, subRun);

        listener.onRunCompleted(subTask, subRun, AgentStatus.COMPLETED);
        store.stepCompleted(subTask, subStep, TaskStatus.COMPLETED, "inner-done");
        listener.onStepCompleted(subTask, subStep);
        store.taskCompleted(subTask, TaskStatus.COMPLETED);
        listener.onTaskCompleted(subTask, TaskStatus.COMPLETED);
        store.stepCompleted(topTask, loopStep, TaskStatus.COMPLETED, "loop-done");
        listener.onStepCompleted(topTask, loopStep);
        store.taskCompleted(topTask, TaskStatus.COMPLETED);
        listener.onTaskCompleted(topTask, TaskStatus.COMPLETED);

        var spans = exporter.getByTaskId(topTask);

        assertFalse(spans.isEmpty(), "Expected spans for the top task");

        var traceIds = spans.stream().map(s -> s.traceId()).distinct().toList();
        assertEquals(1, traceIds.size(),
                "All spans (including sub-task) must share one traceId. Got: " + traceIds);

        var topTaskSpan = spans.stream()
                .filter(s -> s.name().equals("agentican.task")
                        && topTask.equals(s.attributes().get("agentican.task.id")))
                .findFirst().orElseThrow(() -> new AssertionError("no top-task span"));

        var loopStepSpan = spans.stream()
                .filter(s -> s.name().equals("agentican.step loop-step"))
                .findFirst().orElseThrow(() -> new AssertionError("no loop-step span"));

        var subTaskSpan = spans.stream()
                .filter(s -> s.name().equals("agentican.task")
                        && subTask.equals(s.attributes().get("agentican.task.id")))
                .findFirst().orElseThrow(() -> new AssertionError("no sub-task span"));

        var innerStepSpan = spans.stream()
                .filter(s -> s.name().equals("agentican.step inner-step"))
                .findFirst().orElseThrow(() -> new AssertionError("no inner-step span"));

        assertEquals(topTaskSpan.spanId(), loopStepSpan.parentSpanId(),
                "Loop step should be child of top task");
        assertEquals(loopStepSpan.spanId(), subTaskSpan.parentSpanId(),
                "Sub-task span must be child of the dispatching loop step, NOT orphan");
        assertEquals(subTaskSpan.spanId(), innerStepSpan.parentSpanId(),
                "Sub-task's inner step should be child of the sub-task span");
    }

    @Test
    void topLevelTaskRemainsRoot() {

        var exporter = new InMemorySpanExporter();
        var provider = SdkTracerProvider.builder()
                .addSpanProcessor(SimpleSpanProcessor.create(exporter))
                .build();
        var tracer = provider.get("test");

        var store = new TaskStateStoreMemory();
        var listener = new TracedLifecycleListener(tracer, store);

        var taskId = "task-root";
        store.taskStarted(taskId, "root", null, Map.of());
        listener.onTaskStarted(taskId);
        store.taskCompleted(taskId, TaskStatus.COMPLETED);
        listener.onTaskCompleted(taskId, TaskStatus.COMPLETED);

        var spans = exporter.getByTaskId(taskId);
        assertEquals(1, spans.size());

        var taskSpan = spans.getFirst();
        assertNull(taskSpan.parentSpanId(),
                "Top-level task span should be a root (no parent)");
    }
}
