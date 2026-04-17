package ai.agentican.quarkus.otel;

import ai.agentican.framework.Agentican;
import ai.agentican.framework.TaskListener;
import ai.agentican.framework.TaskDecorator;
import ai.agentican.framework.orchestration.model.Plan;
import ai.agentican.framework.orchestration.model.PlanStepAgent;
import ai.agentican.quarkus.test.MockLlmClient;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class TracingIntegrationTest {

    @Inject
    Agentican agentican;

    @Inject
    MockLlmClient mockLlm;

    @Inject
    TaskDecorator taskDecorator;

    @Inject
    Instance<TaskListener> stepListeners;

    @Inject
    InMemorySpanExporter spanExporter;

    @BeforeEach
    void reset() {

        mockLlm.reset();
        spanExporter.clear();
    }

    @Test
    void tracingBeansAreAutoConfigured() {

        assertNotNull(taskDecorator, "TaskDecorator should be produced");
        assertTrue(stepListeners.stream().count() >= 1,
                "Should have at least 1 TaskListener (lifecycle). Got: " + stepListeners.stream().count());
    }

    @Test
    void taskCompletesSuccessfullyWithTracingEnabled() {

        mockLlm.queueEndTurn("Traced result");

        var task = Plan.of("otel-test", "test", List.of(),
                List.of(PlanStepAgent.of("research", "researcher", "do something",
                        List.of(), false, List.of(), List.of())));

        var handle = agentican.run(task);
        var result = handle.result();

        assertEquals("COMPLETED", result.status().name());
    }

    @Test
    void multiStepTaskCompletesWithTracing() {

        mockLlm.queueEndTurn("Step 1 result");
        mockLlm.queueEndTurn("Step 2 result");

        var task = Plan.of("multi-step-trace", "test", List.of(), List.of(
                PlanStepAgent.of("step1", "researcher", "do step 1", List.of(), false, List.of(), List.of()),
                PlanStepAgent.of("step2", "researcher", "do step 2", List.of("step1"), false, List.of(), List.of())));

        var handle = agentican.run(task);

        assertEquals("COMPLETED", handle.result().status().name());
    }

    @Test
    void allSpansShareSameTraceId() throws Exception {

        mockLlm.queueEndTurn("Step 1 done");
        mockLlm.queueEndTurn("Step 2 done");

        var task = Plan.of("trace-id-test", "test", List.of(), List.of(
                PlanStepAgent.of("alpha", "researcher", "do alpha", List.of(), false, List.of(), List.of()),
                PlanStepAgent.of("beta", "researcher", "do beta", List.of("alpha"), false, List.of(), List.of())));

        var handle = agentican.run(task);
        assertEquals("COMPLETED", handle.result().status().name());

        Thread.sleep(500);

        var spans = spanExporter.getByTaskId(handle.taskId());

        assertFalse(spans.isEmpty(), "Should have exported spans");

        var spanNames = spans.stream().map(s -> s.name()).toList();
        assertTrue(spanNames.stream().anyMatch(n -> n.contains("alpha")),
                "Should have span for step alpha. Spans: " + spanNames);
        assertTrue(spanNames.stream().anyMatch(n -> n.contains("beta")),
                "Should have span for step beta. Spans: " + spanNames);

        var traceIds = spans.stream().map(s -> s.traceId()).collect(Collectors.toSet());

        assertEquals(1, traceIds.size(),
                "All spans should share the same traceId. Got: " + traceIds);
    }

    @Test
    void completeSpanTreeIsCorrect() throws Exception {

        mockLlm.queueEndTurn("Step A result");
        mockLlm.queueEndTurn("Step B result");

        var task = Plan.of("full-tree-test", "test", List.of(), List.of(
                PlanStepAgent.of("step-a", "researcher", "do A", List.of(), false, List.of(), List.of()),
                PlanStepAgent.of("step-b", "researcher", "do B", List.of("step-a"), false, List.of(), List.of())));

        var handle = agentican.run(task);
        assertEquals("COMPLETED", handle.result().status().name());

        Thread.sleep(500);

        var spans = spanExporter.getByTaskId(handle.taskId());
        var names = spans.stream().map(s -> s.name()).toList();

        var taskSpans = spans.stream().filter(s -> s.name().equals("agentican.task")).toList();
        var stepSpans = spans.stream().filter(s -> s.name().startsWith("agentican.step")).toList();
        var runSpans = spans.stream().filter(s -> s.name().equals("agentican.run")).toList();
        var turnSpans = spans.stream().filter(s -> s.name().startsWith("agentican.turn")).toList();
        var llmCallSpans = spans.stream().filter(s -> s.name().equals("agentican.llm.call")).toList();
        var llmSendSpans = spans.stream().filter(s -> s.name().equals("agentican.llm.send")).toList();

        assertEquals(1, taskSpans.size(), "Expected 1 task span. All spans: " + names);
        assertEquals(2, stepSpans.size(), "Expected 2 step spans. All spans: " + names);
        assertEquals(2, runSpans.size(), "Expected 2 run spans. All spans: " + names);
        assertEquals(2, turnSpans.size(), "Expected 2 turn spans. All spans: " + names);
        assertEquals(2, llmCallSpans.size(), "Expected 2 llm.call spans. All spans: " + names);
        assertEquals(2, llmSendSpans.size(), "Expected 2 llm.send spans. All spans: " + names);
        assertEquals(11, spans.size(), "Expected exactly 11 spans total. All spans: " + names);

        var traceIds = spans.stream().map(s -> s.traceId()).collect(Collectors.toSet());
        assertEquals(1, traceIds.size(), "All spans should share one traceId");

        var spanIds = spans.stream().map(s -> s.spanId()).collect(Collectors.toSet());
        assertEquals(spans.size(), spanIds.size(), "No duplicate span IDs");

        var taskSpanId = taskSpans.getFirst().spanId();
        for (var step : stepSpans) {
            assertEquals(taskSpanId, step.parentSpanId(),
                    "Step '" + step.name() + "' should be child of task span");
        }
    }

    @Test
    void stepSpansAreNestedUnderTaskSpan() throws Exception {

        mockLlm.queueEndTurn("First done");
        mockLlm.queueEndTurn("Second done");

        var task = Plan.of("nesting-test", "test", List.of(), List.of(
                PlanStepAgent.of("first", "researcher", "do first", List.of(), false, List.of(), List.of()),
                PlanStepAgent.of("second", "researcher", "do second", List.of("first"), false, List.of(), List.of())));

        var handle = agentican.run(task);
        handle.result();

        Thread.sleep(500);

        var spans = spanExporter.getByTaskId(handle.taskId());

        var taskSpans = spans.stream().filter(s -> s.name().equals("agentican.task")).toList();
        var stepSpans = spans.stream().filter(s -> s.name().startsWith("agentican.step")).toList();

        assertEquals(1, taskSpans.size(), "Should have exactly 1 task span");
        assertEquals(2, stepSpans.size(), "Should have 2 step spans");

        var taskSpanId = taskSpans.getFirst().spanId();
        var stepParentIds = stepSpans.stream()
                .map(s -> s.parentSpanId())
                .collect(Collectors.toSet());

        assertEquals(1, stepParentIds.size(),
                "Both step spans should share the same parent");
        assertEquals(taskSpanId, stepParentIds.iterator().next(),
                "Step spans should be children of the task span");
    }
}
