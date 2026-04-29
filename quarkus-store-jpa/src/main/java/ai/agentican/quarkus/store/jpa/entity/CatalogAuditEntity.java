package ai.agentican.quarkus.store.jpa.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "catalog_audit")
public class CatalogAuditEntity extends PanacheEntityBase {

    @Id
    public String id;

    @Column(name = "entity_type", nullable = false)
    public String entityType;

    @Column(name = "entity_ref", nullable = false)
    public String entityRef;

    @Column(nullable = false)
    public String action;

    @Column
    public String actor;

    @Column(name = "before_json", columnDefinition = "TEXT")
    public String beforeJson;

    @Column(name = "after_json", columnDefinition = "TEXT")
    public String afterJson;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt;
}
