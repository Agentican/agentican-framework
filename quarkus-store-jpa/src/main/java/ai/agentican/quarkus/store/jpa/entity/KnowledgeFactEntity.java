package ai.agentican.quarkus.store.jpa.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "knowledge_facts")
public class KnowledgeFactEntity extends PanacheEntityBase {

    @Id
    public String id;

    @Column(name = "entry_id", nullable = false)
    public String entryId;

    @Column(length = 512)
    public String name;

    @Column(columnDefinition = "TEXT")
    public String content;

    @Column(name = "tags_json", columnDefinition = "TEXT")
    public String tagsJson;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    public Instant updatedAt;
}
