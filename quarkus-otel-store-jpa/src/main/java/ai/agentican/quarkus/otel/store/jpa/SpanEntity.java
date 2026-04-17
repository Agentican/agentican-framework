package ai.agentican.quarkus.otel.store.jpa;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "spans")
public class SpanEntity extends PanacheEntityBase {

    @Id
    @Column(name = "span_id")
    public String spanId;

    @Column(name = "trace_id", nullable = false)
    public String traceId;

    @Column(name = "parent_span_id")
    public String parentSpanId;

    @Column(name = "task_id")
    public String taskId;

    @Column(nullable = false)
    public String name;

    public String kind;

    @Column(name = "start_nanos", nullable = false)
    public long startNanos;

    @Column(name = "end_nanos", nullable = false)
    public long endNanos;

    @Column(name = "status_code")
    public String statusCode;

    @Column(name = "attributes_json", columnDefinition = "TEXT")
    public String attributesJson;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt;
}
