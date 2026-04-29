package ai.agentican.quarkus.store.jpa;

import ai.agentican.quarkus.audit.CatalogAuditLog;

import io.quarkus.arc.properties.IfBuildProperty;
import io.quarkus.scheduler.Scheduled;
import io.quarkus.scheduler.Scheduled.ConcurrentExecution;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;

@ApplicationScoped
@IfBuildProperty(name = "agentican.store.backend", stringValue = "jpa", enableIfMissing = true)
public class CatalogAuditRetentionScheduler {

    private static final Logger LOG = LoggerFactory.getLogger(CatalogAuditRetentionScheduler.class);

    @Inject
    CatalogAuditLog audit;

    @ConfigProperty(name = "agentican.audit.retention", defaultValue = "P30D")
    Duration retention;

    @Scheduled(every = "{agentican.audit.prune-every:P1D}",
               concurrentExecution = ConcurrentExecution.SKIP)
    void prune() {

        if (retention == null || retention.isZero() || retention.isNegative()) {
            LOG.debug("Catalog audit retention disabled (retention={})", retention);
            return;
        }

        var cutoff = Instant.now().minus(retention);
        var deleted = audit.prune(cutoff);

        if (deleted > 0)
            LOG.info("Catalog audit scheduler: pruned {} entries older than {} ({})",
                    deleted, cutoff, retention);
    }
}
