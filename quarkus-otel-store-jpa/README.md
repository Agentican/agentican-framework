# Agentican Quarkus OTel Store (JPA)

Persistent span storage for `agentican-quarkus-otel`. Replaces the default
`InMemorySpanExporter` with `JpaSpanExporter`, which writes every exported span
to a `spans` table via Flyway migration `V2__spans.sql`.

Both exporters implement the `SpanStore` interface used by `TracesResource`, so
`GET /agentican/traces/{taskId}` continues to work — only the backing store
changes.

## Setup

```xml
<dependency>
    <groupId>ai.agentican</groupId>
    <artifactId>agentican-quarkus-otel-store-jpa</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```

```properties
quarkus.index-dependency.agentican-otel-store-jpa.group-id=ai.agentican
quarkus.index-dependency.agentican-otel-store-jpa.artifact-id=agentican-quarkus-otel-store-jpa
```

Reuses the datasource and Flyway configuration set up for
`agentican-quarkus-store-jpa` — no extra persistence wiring.

Gated with the same build property:

```java
@IfBuildProperty(name = "agentican.store.backend",
                 stringValue = "jpa",
                 enableIfMissing = true)
```

Set `agentican.store.backend=memory` to fall back to `InMemorySpanExporter`.

## Schema

`V2__spans.sql` adds:

```sql
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
```

Indexed on `trace_id`, `task_id`, and `start_nanos`. The `task_id` column is
populated from the `agentican.task.id` span attribute.

## Queries

`SpanStore` defines the read-side API:

```java
List<SpanView> getByTaskId(String taskId);
List<SpanView> getByTraceId(String traceId);
```

`TracesResource` injects `SpanStore` (not the concrete exporter), so the same
REST endpoints back both backends:

```bash
curl http://localhost:8080/agentican/traces/{taskId}
```

## What's not in MVP

- No retention policy. The `spans` table grows unbounded. Add a scheduled
  delete-older-than job if traces accumulate faster than ops tolerates.
- No sampling / aggregation at write time.
- OTLP export to an external collector is orthogonal — configure
  `quarkus.otel.exporter.otlp.*` separately if you also want traces in Jaeger
  or Tempo.
