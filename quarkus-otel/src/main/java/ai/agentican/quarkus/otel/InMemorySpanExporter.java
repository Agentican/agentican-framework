package ai.agentican.quarkus.otel;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SpanExporter;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class InMemorySpanExporter implements SpanExporter, SpanStore {

    private static final AttributeKey<String> TASK_ID_KEY = AttributeKey.stringKey("agentican.task.id");
    private static final int DEFAULT_MAX_TRACES = 100;

    private final int maxTraces;
    private final Map<String, List<SpanData>> traces = new ConcurrentHashMap<>();
    private final Map<String, String> taskIdToTraceId = new ConcurrentHashMap<>();
    private final Deque<String> traceOrder = new java.util.concurrent.ConcurrentLinkedDeque<>();

    public InMemorySpanExporter() {

        this(DEFAULT_MAX_TRACES);
    }

    public InMemorySpanExporter(int maxTraces) {

        this.maxTraces = maxTraces;
    }

    @Override
    public CompletableResultCode export(Collection<SpanData> spans) {

        for (var span : spans) {

            var traceId = span.getTraceId();

            traces.computeIfAbsent(traceId, k -> {

                traceOrder.addLast(k);

                evictIfNeeded();

                return new CopyOnWriteArrayList<>();

            }).add(span);

            var taskId = span.getAttributes().get(TASK_ID_KEY);

            if (taskId != null && !taskId.isBlank())
                taskIdToTraceId.putIfAbsent(taskId, traceId);
        }

        return CompletableResultCode.ofSuccess();
    }

    @Override
    public CompletableResultCode flush() {

        return CompletableResultCode.ofSuccess();
    }

    @Override
    public CompletableResultCode shutdown() {

        traces.clear();
        taskIdToTraceId.clear();
        traceOrder.clear();

        return CompletableResultCode.ofSuccess();
    }

    @Override
    public List<SpanView> getByTaskId(String taskId) {

        var traceId = taskIdToTraceId.get(taskId);
        var spans = traceId != null ? traces.getOrDefault(traceId, List.of()) : List.<SpanData>of();

        return spans.stream().map(SpanView::of).toList();
    }

    @Override
    public List<SpanView> getByTraceId(String traceId) {

        return traces.getOrDefault(traceId, List.<SpanData>of()).stream().map(SpanView::of).toList();
    }

    public void clear() {

        traces.clear();
        taskIdToTraceId.clear();
        traceOrder.clear();
    }

    private void evictIfNeeded() {

        while (traceOrder.size() > maxTraces) {

            var evicted = traceOrder.pollFirst();

            if (evicted != null) {

                traces.remove(evicted);

                taskIdToTraceId.values().removeIf(traceId -> traceId.equals(evicted));
            }
        }
    }
}
