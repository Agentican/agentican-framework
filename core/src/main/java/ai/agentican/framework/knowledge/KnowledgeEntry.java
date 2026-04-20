package ai.agentican.framework.knowledge;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

public class KnowledgeEntry {

    private final String id;
    private final Instant created;

    private final List<KnowledgeFact> facts = new CopyOnWriteArrayList<>();

    private String name;
    private String description;

    private volatile KnowledgeStatus status;
    private volatile Instant updated;

    public KnowledgeEntry(String id, String name, String description) {

        this(id, name, description, KnowledgeStatus.INDEXING, Instant.now(), null);
    }

    public KnowledgeEntry(String id, String name, String description, KnowledgeStatus status,
                          Instant created, Instant updated) {

        if (id == null || id.isBlank())
            throw new IllegalArgumentException("Entry id is required");

        if (name == null || name.isBlank())
            throw new IllegalArgumentException("Entry name is required");

        this.id = id;
        this.name = name;
        this.description = description != null ? description : "";
        this.status = status != null ? status : KnowledgeStatus.INDEXING;
        this.created = created != null ? created : Instant.now();
        this.updated = updated != null ? updated : this.created;
    }

    public static KnowledgeEntry of(String name, String description) {

        return new KnowledgeEntry(UUID.randomUUID().toString(), name, description);
    }

    public String id() { return id; }
    public String name() { return name; }
    public String description() { return description; }
    public KnowledgeStatus status() { return status; }
    public List<KnowledgeFact> facts() { return List.copyOf(facts); }
    public Instant created() { return created; }
    public Instant updated() { return updated; }

    public void setName(String name) {

        this.name = name;
        touch();
    }

    public void setDescription(String description) {

        this.description = description != null ? description : "";
        touch();
    }

    public void setStatus(KnowledgeStatus status) {

        this.status = status;
        touch();
    }

    public void addFact(KnowledgeFact fact) {

        facts.add(fact);
        touch();
    }

    public void clearFacts() {

        facts.clear();
        touch();
    }

    private void touch() {

        this.updated = Instant.now();
    }
}
