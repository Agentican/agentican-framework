package ai.agentican.quarkus.store.jpa.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;

@Entity
@Table(name = "tasks")
public class TaskEntity extends PanacheEntityBase {

    @Id
    public String id;

    @Column(name = "plan_id")
    public String planId;

    @Column(name = "task_name")
    public String taskName;

    @Column(name = "parent_task_id")
    public String parentTaskId;

    @Column(name = "parent_step_id")
    public String parentStepId;

    @Column(name = "iteration_index", nullable = false)
    public int iterationIndex;

    public String status;

    @Column(name = "params_json", columnDefinition = "TEXT")
    public String paramsJson;

    @Column(name = "plan_snapshot_json", columnDefinition = "TEXT")
    public String planSnapshotJson;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt;

    @Column(name = "completed_at")
    public Instant completedAt;

    @Version
    public long version;
}
