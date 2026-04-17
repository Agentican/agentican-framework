package ai.agentican.quarkus.store.jpa.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "agents")
public class AgentEntity extends PanacheEntityBase {

    @Id
    public String id;

    @Column(name = "external_id", unique = true)
    public String externalId;

    @Column(nullable = false)
    public String name;

    @Column(columnDefinition = "TEXT")
    public String role;

    @Column
    public String llm;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    public Instant updatedAt;
}
