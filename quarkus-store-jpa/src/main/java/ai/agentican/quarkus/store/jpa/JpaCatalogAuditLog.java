package ai.agentican.quarkus.store.jpa;

import ai.agentican.framework.util.Ids;
import ai.agentican.quarkus.audit.CatalogAuditLog;
import ai.agentican.quarkus.store.jpa.entity.CatalogAuditEntity;

import io.quarkus.arc.properties.IfBuildProperty;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@ApplicationScoped
@IfBuildProperty(name = "agentican.store.backend", stringValue = "jpa", enableIfMissing = true)
public class JpaCatalogAuditLog implements CatalogAuditLog {

    private static final Logger LOG = LoggerFactory.getLogger(JpaCatalogAuditLog.class);

    @Override
    @Transactional
    public void record(String entityType, String entityRef, String action,
                       String actor, String beforeJson, String afterJson) {

        try {
            var e = new CatalogAuditEntity();
            e.id = Ids.generate();
            e.entityType = entityType;
            e.entityRef  = entityRef;
            e.action     = action;
            e.actor      = actor;
            e.beforeJson = beforeJson;
            e.afterJson  = afterJson;
            e.createdAt  = Instant.now();
            e.persist();
        }
        catch (Exception ex) {
            LOG.warn("Failed to persist catalog audit entry ({} {} {}): {}",
                    entityType, entityRef, action, ex.getMessage());
        }
    }

    @Override
    @Transactional
    public int prune(Instant before) {

        if (before == null) return 0;

        var count = (int) CatalogAuditEntity.delete("createdAt < ?1", before);

        if (count > 0)
            LOG.info("Catalog audit prune: removed {} entries older than {}", count, before);

        return count;
    }

    @Override
    @Transactional
    public List<AuditEntry> list(String entityType, String entityRef, Instant since, int limit) {

        var params = new java.util.LinkedHashMap<String, Object>();
        var where  = new ArrayList<String>();

        if (entityType != null && !entityType.isBlank()) {
            where.add("entityType = :entityType");
            params.put("entityType", entityType);
        }
        if (entityRef != null && !entityRef.isBlank()) {
            where.add("entityRef = :entityRef");
            params.put("entityRef", entityRef);
        }
        if (since != null) {
            where.add("createdAt >= :since");
            params.put("since", since);
        }

        var query = where.isEmpty() ? "1=1" : String.join(" and ", where);

        List<CatalogAuditEntity> rows = CatalogAuditEntity
                .find(query + " order by createdAt desc", params)
                .page(0, Math.max(1, Math.min(limit, 500)))
                .list();

        var out = new ArrayList<AuditEntry>();
        for (var r : rows)
            out.add(new AuditEntry(r.id, r.entityType, r.entityRef, r.action,
                    r.actor, r.beforeJson, r.afterJson, r.createdAt));
        return out;
    }
}
