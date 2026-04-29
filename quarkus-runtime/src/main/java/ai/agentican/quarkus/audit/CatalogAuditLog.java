package ai.agentican.quarkus.audit;

import java.time.Instant;
import java.util.List;

public interface CatalogAuditLog {

    String AGENT = "agent";
    String SKILL = "skill";
    String PLAN  = "plan";

    String CREATED  = "created";
    String UPDATED  = "updated";
    String DELETED  = "deleted";
    String IMPORTED = "imported";

    void record(String entityType, String entityRef, String action,
                String actor, String beforeJson, String afterJson);

    default List<AuditEntry> list(String entityType, String entityRef, Instant since, int limit) {

        return List.of();
    }

    default int prune(Instant before) {

        return 0;
    }

    record AuditEntry(
            String id,
            String entityType,
            String entityRef,
            String action,
            String actor,
            String beforeJson,
            String afterJson,
            Instant createdAt) {}
}
