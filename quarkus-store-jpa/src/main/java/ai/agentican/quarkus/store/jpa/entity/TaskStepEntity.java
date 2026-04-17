package ai.agentican.quarkus.store.jpa.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "task_steps")
public class TaskStepEntity extends PanacheEntityBase {

    @Id
    public String id;

    @Column(name = "task_id", nullable = false)
    public String taskId;

    @Column(name = "plan_step_id")
    public String planStepId;

    @Column(name = "step_name")
    public String stepName;

    public String status;

    @Column(columnDefinition = "TEXT")
    public String output;

    @Column(name = "aggregate_token_usage_json", columnDefinition = "TEXT")
    public String aggregateTokenUsageJson;

    @Column(name = "checkpoint_json", columnDefinition = "TEXT")
    public String checkpointJson;

    @Column(name = "branch_chosen_path")
    public String branchChosenPath;

    @Column(name = "hitl_response_json", columnDefinition = "TEXT")
    public String hitlResponseJson;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt;

    @Column(name = "completed_at")
    public Instant completedAt;
}
