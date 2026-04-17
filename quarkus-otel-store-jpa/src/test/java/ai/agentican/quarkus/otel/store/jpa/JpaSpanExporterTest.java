package ai.agentican.quarkus.otel.store.jpa;

import ai.agentican.quarkus.otel.SpanStore;
import ai.agentican.quarkus.otel.SpanView;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.sdk.testing.trace.TestSpanData;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.data.StatusData;
import io.opentelemetry.api.trace.SpanKind;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class JpaSpanExporterTest {

    @Inject
    JpaSpanExporter exporter;

    @Inject
    SpanStore spanStore;

    @Test
    void interfaceResolvesToJpaBean() {

        assertSame(exporter, spanStore);
    }

    @Test
    void exportPersistsAndReadsBackByTaskId() {

        var task = "task-" + System.nanoTime();
        var trace = "a".repeat(32);
        var span1 = makeSpan("1".repeat(16), trace, null, "agentican.task", 1_000_000L, 5_000_000L, task);
        var span2 = makeSpan("2".repeat(16), trace, "1".repeat(16), "agentican.step", 2_000_000L, 4_000_000L, task);

        exporter.export(List.of(span1, span2));

        var views = exporter.getByTaskId(task);
        assertEquals(2, views.size());
        assertEquals("agentican.task", views.get(0).name());
        assertEquals("agentican.step", views.get(1).name());
        assertEquals(trace, views.get(0).traceId());
        assertEquals("1".repeat(16), views.get(1).parentSpanId());
    }

    @Test
    void getByTaskIdReturnsAllSpansInTraceIncludingUntaggedChildren() {

        // Real agent runs stamp `agentican.task.id` on task/step spans but NOT on
        // run/turn/llm/tool spans — those rely on OTel context propagation for their parent
        // linkage. getByTaskId must still return the full waterfall for that trace.
        var task = "task-mix-" + System.nanoTime();
        var trace = "e".repeat(32);
        var tagged = makeSpan("e1234567abcdef01", trace, null, "agentican.task", 1_000_000L, 9_000_000L, task);
        var untaggedRun = TestSpanData.builder()
                .setName("agentican.run")
                .setSpanContext(ctx("e1234567abcdef02", trace))
                .setParentSpanContext(ctx("e1234567abcdef01", trace))
                .setHasEnded(true)
                .setStartEpochNanos(2_000_000L)
                .setEndEpochNanos(8_000_000L)
                .setStatus(StatusData.ok())
                .setKind(SpanKind.INTERNAL)
                .setAttributes(Attributes.empty())
                .build();
        var untaggedTurn = TestSpanData.builder()
                .setName("agentican.turn 0")
                .setSpanContext(ctx("e1234567abcdef03", trace))
                .setParentSpanContext(ctx("e1234567abcdef02", trace))
                .setHasEnded(true)
                .setStartEpochNanos(3_000_000L)
                .setEndEpochNanos(7_000_000L)
                .setStatus(StatusData.ok())
                .setKind(SpanKind.INTERNAL)
                .setAttributes(Attributes.empty())
                .build();

        exporter.export(List.of(tagged, untaggedRun, untaggedTurn));

        var views = exporter.getByTaskId(task);
        assertEquals(3, views.size(), "All spans in the trace must be returned, not just tagged ones");
        assertEquals("agentican.task", views.get(0).name());
        assertEquals("agentican.run", views.get(1).name());
        assertEquals("agentican.turn 0", views.get(2).name());
    }

    @Test
    void getByTaskIdIncludesSubTaskSpansInTheSameTrace() {

        // Sub-tasks (loop iterations, branch paths) run with their own taskId but share the
        // parent task's traceId because TracedLifecycleListener explicitly parents the sub-task's
        // task span under the dispatching parent step. Querying by the ROOT taskId must return
        // every span in the unified trace: parent task/step/run AND sub-task task/step/turn.
        var trace = "f".repeat(32);
        var parentTask = "parent-" + System.nanoTime();
        var subTask = "sub-" + System.nanoTime();

        var parentTaskSpan = makeSpan("f0000000aaaaaa01", trace, null,
                "agentican.task", 1_000_000L, 20_000_000L, parentTask);
        var parentStepSpan = makeSpan("f0000000aaaaaa02", trace, "f0000000aaaaaa01",
                "agentican.step loop-step", 2_000_000L, 19_000_000L, parentTask);
        var untaggedParentRun = TestSpanData.builder()
                .setName("agentican.run")
                .setSpanContext(ctx("f0000000aaaaaa03", trace))
                .setParentSpanContext(ctx("f0000000aaaaaa02", trace))
                .setHasEnded(true)
                .setStartEpochNanos(3_000_000L).setEndEpochNanos(4_000_000L)
                .setStatus(StatusData.ok()).setKind(SpanKind.INTERNAL)
                .setAttributes(Attributes.empty())
                .build();
        var subTaskSpan = makeSpan("f0000000bbbbbb01", trace, "f0000000aaaaaa02",
                "agentican.task", 5_000_000L, 18_000_000L, subTask);
        var subStepSpan = makeSpan("f0000000bbbbbb02", trace, "f0000000bbbbbb01",
                "agentican.step inner", 6_000_000L, 17_000_000L, subTask);
        var untaggedSubTurn = TestSpanData.builder()
                .setName("agentican.turn 0")
                .setSpanContext(ctx("f0000000bbbbbb03", trace))
                .setParentSpanContext(ctx("f0000000bbbbbb02", trace))
                .setHasEnded(true)
                .setStartEpochNanos(7_000_000L).setEndEpochNanos(8_000_000L)
                .setStatus(StatusData.ok()).setKind(SpanKind.INTERNAL)
                .setAttributes(Attributes.empty())
                .build();

        exporter.export(List.of(parentTaskSpan, parentStepSpan, untaggedParentRun,
                subTaskSpan, subStepSpan, untaggedSubTurn));

        var viaParent = exporter.getByTaskId(parentTask);
        assertEquals(6, viaParent.size(),
                "Querying by parent task id must return all 6 spans in the unified trace");
        var parentNames = viaParent.stream().map(SpanView::name).toList();
        assertTrue(parentNames.contains("agentican.step inner"),
                "Sub-task step span should be reachable from parent taskId. Got: " + parentNames);

        // Asymmetric-but-correct: querying by the child taskId returns the same unified trace.
        var viaChild = exporter.getByTaskId(subTask);
        assertEquals(6, viaChild.size(),
                "Querying by sub-task id returns the whole trace — same semantics as in-memory exporter");
    }

    @Test
    void getByTraceIdReturnsAllSpansOrderedByStart() {

        var trace = "b".repeat(32);
        var spanA = makeSpan("a".repeat(16), trace, null, "root", 10_000_000L, 20_000_000L, "task-x");
        var spanB = makeSpan("b".repeat(16), trace, "a".repeat(16), "child", 15_000_000L, 18_000_000L, "task-x");

        exporter.export(List.of(spanB, spanA));

        var views = exporter.getByTraceId(trace);
        assertEquals(2, views.size());
        assertEquals("root", views.get(0).name());
        assertEquals("child", views.get(1).name());
    }

    @Test
    void attributesRoundTripAsStringMap() {

        var trace = "c".repeat(32);
        var span = TestSpanData.builder()
                .setName("test-span")
                .setSpanContext(ctx("d".repeat(16), trace))
                .setHasEnded(true)
                .setStartEpochNanos(1L)
                .setEndEpochNanos(2L)
                .setStatus(StatusData.ok())
                .setKind(SpanKind.INTERNAL)
                .setAttributes(Attributes.builder()
                        .put(AttributeKey.stringKey("agentican.task.id"), "task-attr-test")
                        .put(AttributeKey.stringKey("custom.key"), "custom.value")
                        .build())
                .build();

        exporter.export(List.of(span));

        var views = exporter.getByTaskId("task-attr-test");
        assertEquals(1, views.size());
        assertEquals("custom.value", views.getFirst().attributes().get("custom.key"));
    }

    private static SpanData makeSpan(String spanId, String traceId, String parentSpanId, String name,
                                     long startNanos, long endNanos, String taskId) {

        var builder = TestSpanData.builder()
                .setName(name)
                .setSpanContext(ctx(spanId, traceId))
                .setHasEnded(true)
                .setStartEpochNanos(startNanos)
                .setEndEpochNanos(endNanos)
                .setStatus(StatusData.ok())
                .setKind(SpanKind.INTERNAL)
                .setAttributes(Attributes.of(AttributeKey.stringKey("agentican.task.id"), taskId));

        if (parentSpanId != null)
            builder.setParentSpanContext(ctx(parentSpanId, traceId));

        return builder.build();
    }

    private static io.opentelemetry.api.trace.SpanContext ctx(String spanId, String traceId) {

        return io.opentelemetry.api.trace.SpanContext.create(
                traceId, spanId,
                io.opentelemetry.api.trace.TraceFlags.getSampled(),
                io.opentelemetry.api.trace.TraceState.getDefault());
    }
}
