package ai.agentican.quarkus.rest.resource;

import ai.agentican.quarkus.audit.CatalogAuditLog;
import ai.agentican.quarkus.audit.CatalogAuditLog.AuditEntry;

import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Path("/agentican/audit")
@Produces(MediaType.APPLICATION_JSON)
public class AuditResource {

    @Inject
    CatalogAuditLog audit;

    @GET
    public List<AuditEntry> list(@QueryParam("entityType")                   String entityType,
                                  @QueryParam("entityRef")                    String entityRef,
                                  @QueryParam("since")                        String sinceIso,
                                  @QueryParam("limit") @DefaultValue("100")   int    limit) {

        Instant since = null;
        if (sinceIso != null && !sinceIso.isBlank()) {
            try { since = Instant.parse(sinceIso); }
            catch (Exception ignored) {}
        }

        return audit.list(entityType, entityRef, since, limit);
    }

    @DELETE
    public Map<String, Integer> prune(@QueryParam("before")    String beforeIso,
                                       @QueryParam("olderThan") String olderThanIso) {

        Instant cutoff;

        if (beforeIso != null && !beforeIso.isBlank()) {
            try { cutoff = Instant.parse(beforeIso); }
            catch (Exception e) {
                throw new BadRequestException("Invalid 'before' (expected ISO-8601 instant): " + e.getMessage());
            }
        }
        else if (olderThanIso != null && !olderThanIso.isBlank()) {
            try {
                var d = Duration.parse(olderThanIso);
                cutoff = Instant.now().minus(d);
            }
            catch (Exception e) {
                throw new BadRequestException("Invalid 'olderThan' (expected ISO-8601 duration like P30D): " + e.getMessage());
            }
        }
        else {
            throw new BadRequestException("Must supply either ?before=ISO_INSTANT or ?olderThan=ISO_DURATION");
        }

        return Map.of("deleted", audit.prune(cutoff));
    }
}
