package ai.agentican.quarkus.otel;

import java.util.List;

public interface SpanStore {

    List<SpanView> getByTaskId(String taskId);

    List<SpanView> getByTraceId(String traceId);
}
