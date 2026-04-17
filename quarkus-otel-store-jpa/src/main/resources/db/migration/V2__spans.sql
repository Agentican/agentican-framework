-- Persistent OpenTelemetry span storage. Writes happen through JpaSpanExporter when the
-- otel-store-jpa module is on the classpath and Pattern A selects the JPA backend. A span row
-- carries the minimum needed for TracesResource queries (by task_id or trace_id) plus a JSON
-- blob of attributes for flexible read-side filtering.
--
-- No retention policy in MVP — the table grows unbounded. Add a scheduled delete-older-than job
-- later if traces accumulate faster than ops tolerates.

CREATE TABLE spans (
    span_id          VARCHAR(64)  PRIMARY KEY,
    trace_id         VARCHAR(64)  NOT NULL,
    parent_span_id   VARCHAR(64),
    task_id          VARCHAR(255),
    name             VARCHAR(255) NOT NULL,
    kind             VARCHAR(32),
    start_nanos      BIGINT       NOT NULL,
    end_nanos        BIGINT       NOT NULL,
    status_code      VARCHAR(32),
    attributes_json  TEXT,
    created_at       TIMESTAMP    NOT NULL
);

CREATE INDEX idx_spans_trace ON spans (trace_id);
CREATE INDEX idx_spans_task ON spans (task_id);
CREATE INDEX idx_spans_start ON spans (start_nanos);
