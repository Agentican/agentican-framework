package ai.agentican.quarkus.store.jpa.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "tool_results")
public class ToolResultEntity extends PanacheEntityBase {

    @Id
    public String id;

    @Column(name = "turn_id", nullable = false)
    public String turnId;

    @Column(name = "tool_call_id", nullable = false)
    public String toolCallId;

    @Column(name = "tool_name", nullable = false)
    public String toolName;

    @Column(columnDefinition = "TEXT")
    public String content;

    @Column(name = "is_error", nullable = false)
    public boolean isError;

    @Column(nullable = false)
    public String state;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt;
}
