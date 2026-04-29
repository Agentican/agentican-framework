package ai.agentican.quarkus.audit;

import io.quarkus.arc.DefaultBean;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
@DefaultBean
public class NoOpCatalogAuditLog implements CatalogAuditLog {

    @Override
    public void record(String entityType, String entityRef, String action,
                       String actor, String beforeJson, String afterJson) {

    }
}
