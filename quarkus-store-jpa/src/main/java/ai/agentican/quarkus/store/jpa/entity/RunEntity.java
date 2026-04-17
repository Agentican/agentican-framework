package ai.agentican.quarkus.store.jpa.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "runs")
public class RunEntity extends PanacheEntityBase {

    @Id
    public String id;

    @Column(name = "task_step_id", nullable = false)
    public String taskStepId;

    @Column(name = "agent_id")
    public String agentId;

    @Column(name = "agent_name")
    public String agentName;

    @Column(name = "run_index", nullable = false)
    public int runIndex;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt;

    @Column(name = "completed_at")
    public Instant completedAt;
}
