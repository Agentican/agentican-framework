package ai.agentican.quarkus.store.jpa.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "tools")
public class ToolEntity extends PanacheEntityBase {

    @Id
    public String id;

    @Column(nullable = false, unique = true)
    public String name;

    @Column(columnDefinition = "TEXT")
    public String description;

    @Column(name = "properties_json", columnDefinition = "TEXT")
    public String propertiesJson;

    @Column(name = "required_json", columnDefinition = "TEXT")
    public String requiredJson;

    public String source;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt;
}
