package ai.agentican.quarkus.otel.store.jpa;

import ai.agentican.framework.util.Json;
import ai.agentican.quarkus.otel.SpanStore;
import ai.agentican.quarkus.otel.SpanView;

import com.fasterxml.jackson.core.type.TypeReference;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import io.quarkus.arc.properties.IfBuildProperty;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@ApplicationScoped
@IfBuildProperty(name = "agentican.store.backend", stringValue = "jpa", enableIfMissing = true)
public class JpaSpanExporter implements SpanExporter, SpanStore {

    private static final Logger LOG = LoggerFactory.getLogger(JpaSpanExporter.class);
    private static final AttributeKey<String> TASK_ID_KEY = AttributeKey.stringKey("agentican.task.id");
    private static final TypeReference<Map<String, String>> ATTRS_TYPE = new TypeReference<>() {};

    @Override
    @Transactional
    public CompletableResultCode export(Collection<SpanData> spans) {

        for (var span : spans) {

            var e = new SpanEntity();
            e.spanId = span.getSpanId();
            e.traceId = span.getTraceId();
            e.parentSpanId = span.getParentSpanContext().isValid()
                    ? span.getParentSpanContext().getSpanId()
                    : null;
            e.taskId = span.getAttributes().get(TASK_ID_KEY);
            e.name = span.getName();
            e.kind = span.getKind() != null ? span.getKind().name() : null;
            e.startNanos = span.getStartEpochNanos();
            e.endNanos = span.getEndEpochNanos();
            e.statusCode = span.getStatus() != null ? span.getStatus().getStatusCode().name() : null;
            e.attributesJson = serializeAttrs(span);
            e.createdAt = Instant.now();

            e.persist();
        }

        return CompletableResultCode.ofSuccess();
    }

    @Override
    public CompletableResultCode flush() {

        return CompletableResultCode.ofSuccess();
    }

    @Override
    public CompletableResultCode shutdown() {

        return CompletableResultCode.ofSuccess();
    }

    @Override
    @Transactional
    public List<SpanView> getByTaskId(String taskId) {

        // Only the task and step spans carry the `agentican.task.id` attribute at write time —
        // run/turn/llm/tool/hitl spans inherit their parent via OTel context propagation, not via
        // a duplicated attribute. Resolve taskId → traceId first, then return every span in that
        // trace so the waterfall includes all levels. Matches InMemorySpanExporter semantics.
        SpanEntity anchor = SpanEntity.find("taskId", taskId).firstResult();
        if (anchor == null) return List.of();

        List<SpanEntity> rows = SpanEntity.list("traceId = ?1 ORDER BY startNanos ASC", anchor.traceId);
        return rows.stream().map(JpaSpanExporter::toView).toList();
    }

    @Override
    @Transactional
    public List<SpanView> getByTraceId(String traceId) {

        List<SpanEntity> rows = SpanEntity.list("traceId = ?1 ORDER BY startNanos ASC", traceId);
        return rows.stream().map(JpaSpanExporter::toView).toList();
    }

    private static SpanView toView(SpanEntity e) {

        var durationMs = (e.endNanos - e.startNanos) / 1_000_000;
        return new SpanView(
                e.spanId,
                e.traceId,
                e.parentSpanId,
                e.name,
                e.startNanos,
                e.endNanos,
                durationMs,
                e.statusCode,
                parseAttrs(e.attributesJson));
    }

    private static String serializeAttrs(SpanData span) {

        var attrs = new LinkedHashMap<String, String>();
        span.getAttributes().forEach((key, value) -> attrs.put(key.getKey(), String.valueOf(value)));

        if (attrs.isEmpty()) return "{}";

        try { return Json.writeValueAsString(attrs); }
        catch (Exception ex) {
            LOG.warn("Failed to serialize span attributes: {}", ex.getMessage());
            return "{}";
        }
    }

    private static Map<String, String> parseAttrs(String json) {

        if (json == null || json.isBlank()) return Map.of();
        try { return Json.mapper().readValue(json, ATTRS_TYPE); }
        catch (Exception ex) {
            LOG.warn("Failed to parse span attributes JSON: {}", ex.getMessage());
            return Map.of();
        }
    }
}
