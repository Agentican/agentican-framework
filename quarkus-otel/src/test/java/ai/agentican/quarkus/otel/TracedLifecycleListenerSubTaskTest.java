package ai.agentican.quarkus.otel;

import ai.agentican.framework.agent.AgentStatus;
import ai.agentican.framework.event.RunCompleted;
import ai.agentican.framework.event.RunStarted;
import ai.agentican.framework.event.StepCompleted;
import ai.agentican.framework.event.StepStarted;
import ai.agentican.framework.event.TaskCompleted;
import ai.agentican.framework.event.TaskStarted;
import ai.agentican.framework.llm.TokenUsage;
import ai.agentican.framework.orchestration.execution.WorkflowRunStatus;
import ai.agentican.framework.state.RuntimeOwner;

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

        var listener = new TracedLifecycleListener(tracer);

        var topTask = "task-top";
        var loopStep = "step-loop";

        listener.on(new TaskStarted(topTask, "top", null, Map.of(), null, null, 0, RuntimeOwner.IN_PROCESS, null));
        listener.on(new StepStarted(topTask, loopStep, "loop-step"));

        var subTask = "task-iter-1";
        var subStep = "step-inner";
        var subRun = "run-inner";

        listener.on(new TaskStarted(subTask, "iter-1", null, Map.of(), topTask, loopStep, 0, RuntimeOwner.IN_PROCESS, null));
        listener.on(new StepStarted(subTask, subStep, "inner-step"));
        listener.on(new RunStarted(subTask, subStep, subRun, "inner-agent"));
        listener.on(new RunCompleted(subTask, subStep, subRun, AgentStatus.COMPLETED, TokenUsage.ZERO));
        listener.on(new StepCompleted(subTask, subStep, "inner-step",
                WorkflowRunStatus.COMPLETED, "inner-done"));
        listener.on(new TaskCompleted(subTask, WorkflowRunStatus.COMPLETED));
        listener.on(new StepCompleted(topTask, loopStep, "loop-step",
                WorkflowRunStatus.COMPLETED, "loop-done"));
        listener.on(new TaskCompleted(topTask, WorkflowRunStatus.COMPLETED));

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

        var listener = new TracedLifecycleListener(tracer);

        var taskId = "task-root";
        listener.on(new TaskStarted(taskId, "root", null, Map.of(), null, null, 0, RuntimeOwner.IN_PROCESS, null));
        listener.on(new TaskCompleted(taskId, WorkflowRunStatus.COMPLETED));

        var spans = exporter.getByTaskId(taskId);
        assertEquals(1, spans.size());

        var taskSpan = spans.getFirst();
        assertNull(taskSpan.parentSpanId(),
                "Top-level task span should be a root (no parent)");
    }
}
