package ai.agentican.quarkus.store.jpa.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "knowledge_entries")
public class KnowledgeEntryEntity extends PanacheEntityBase {

    @Id
    public String id;

    @Column(nullable = false, length = 512)
    public String name;

    @Column(columnDefinition = "TEXT")
    public String description;

    @Column(nullable = false, length = 32)
    public String status;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    public Instant updatedAt;
}
