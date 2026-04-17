package ai.agentican.quarkus.otel;

import io.opentelemetry.sdk.trace.data.SpanData;

import java.util.LinkedHashMap;
import java.util.Map;

public record SpanView(
        String spanId,
        String traceId,
        String parentSpanId,
        String name,
        long startTimeUnixNano,
        long endTimeUnixNano,
        long durationMs,
        String statusCode,
        Map<String, String> attributes) {

    public static SpanView of(SpanData span) {

        var startNano = span.getStartEpochNanos();
        var endNano = span.getEndEpochNanos();
        var durationMs = (endNano - startNano) / 1_000_000;

        var parentId = span.getParentSpanContext().isValid()
                ? span.getParentSpanContext().getSpanId()
                : null;

        var attrs = new LinkedHashMap<String, String>();

        span.getAttributes().forEach((key, value) ->
                attrs.put(key.getKey(), String.valueOf(value)));

        return new SpanView(
                span.getSpanId(),
                span.getTraceId(),
                parentId,
                span.getName(),
                startNano,
                endNano,
                durationMs,
                span.getStatus().getStatusCode().name(),
                attrs);
    }
}
