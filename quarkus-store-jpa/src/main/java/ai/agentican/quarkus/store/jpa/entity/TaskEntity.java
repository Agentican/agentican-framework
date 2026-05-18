package ai.agentican.quarkus.store.jpa.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;

@Entity
@Table(name = "workflow_runs")
public class TaskEntity extends PanacheEntityBase {

    @Id
    public String id;

    @Column(name = "workflow_id")
    public String planId;

    @Column(name = "workflow_run_name")
    public String taskName;

    @Column(name = "parent_workflow_run_id")
    public String parentTaskId;

    @Column(name = "parent_step_id")
    public String parentStepId;

    @Column(name = "iteration_index", nullable = false)
    public int iterationIndex;

    public String status;

    @Column(name = "params_json", columnDefinition = "TEXT")
    public String paramsJson;

    @Column(name = "workflow_snapshot_json", columnDefinition = "TEXT")
    public String planSnapshotJson;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt;

    @Column(name = "completed_at")
    public Instant completedAt;

    @Column(name = "runtime", nullable = false, length = 16)
    public String runtime;

    @Column(name = "temporal_workflow_id")
    public String temporalWorkflowId;

    @Version
    public long version;
}
