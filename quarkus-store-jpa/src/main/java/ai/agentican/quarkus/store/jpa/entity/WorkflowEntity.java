package ai.agentican.quarkus.store.jpa.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "workflows")
public class WorkflowEntity extends PanacheEntityBase {

    @Id
    public String id;

    @Column(nullable = false)
    public String name;

    @Column(columnDefinition = "TEXT")
    public String description;

    @Column(name = "definition_json", columnDefinition = "TEXT", nullable = false)
    public String definitionJson;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt;
}
