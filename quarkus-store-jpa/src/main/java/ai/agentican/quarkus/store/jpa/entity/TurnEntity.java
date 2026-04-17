package ai.agentican.quarkus.store.jpa.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "turns")
public class TurnEntity extends PanacheEntityBase {

    @Id
    public String id;

    @Column(name = "run_id", nullable = false)
    public String runId;

    @Column(name = "turn_index", nullable = false)
    public int turnIndex;

    @Column(name = "request_json", columnDefinition = "TEXT")
    public String requestJson;

    @Column(name = "response_json", columnDefinition = "TEXT")
    public String responseJson;

    @Column(name = "message_id")
    public String messageId;

    @Column(name = "response_id")
    public String responseId;

    @Column(nullable = false)
    public String state;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt;

    @Column(name = "completed_at")
    public Instant completedAt;
}
