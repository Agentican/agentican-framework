package ai.agentican.quarkus.rest.resource;

import ai.agentican.framework.Agentican;
import ai.agentican.framework.config.SkillConfig;
import ai.agentican.framework.util.Ids;
import ai.agentican.quarkus.audit.CatalogAuditLog;
import ai.agentican.quarkus.rest.catalog.CatalogReferences;
import ai.agentican.quarkus.rest.dto.CreateSkillRequest;
import ai.agentican.quarkus.rest.dto.SkillSummary;
import ai.agentican.quarkus.rest.dto.UpdateSkillRequest;
import ai.agentican.quarkus.rest.error.CatalogConflictException;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Path("/agentican/skills")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class SkillsResource {

    @Inject
    Agentican agentican;

    @Inject
    ai.agentican.framework.config.CatalogConfig catalogConfig;

    @Inject
    CatalogAuditLog audit;

    @Inject
    ObjectMapper objectMapper;

    @GET
    public List<SkillSummary> list() {

        var configNames = configDeclaredNames();

        return agentican.registry().skills().list().stream()
                .map(s -> SkillSummary.of(s, configNames.contains(s.name())))
                .toList();
    }

    @GET
    @Path("/{ref}")
    public SkillSummary get(@PathParam("ref") String ref) {

        var skill = resolve(ref);
        if (skill == null) throw new NotFoundException("No skill with id or name: " + ref);

        return SkillSummary.of(skill, configDeclaredNames().contains(skill.name()));
    }

    @POST
    public Response create(CreateSkillRequest request) {

        requireNonBlank(request.name(),        "name");
        requireNonBlank(request.instructions(), "instructions");

        var skills = agentican.registry().skills();
        if (skills.byName(request.name()) != null)
            throw new CatalogConflictException("already_exists",
                    "Skill with name '" + request.name() + "' already exists");

        var cfg = new SkillConfig(Ids.generate(), request.name(), request.instructions());
        skills.register(cfg);

        var created = skills.byName(request.name());
        audit.record(CatalogAuditLog.SKILL, created.id(), CatalogAuditLog.CREATED,
                null, null, toJson(cfg));

        return Response.status(Response.Status.CREATED)
                .entity(SkillSummary.of(created, false))
                .build();
    }

    @PUT
    @Path("/{ref}")
    public SkillSummary update(@PathParam("ref") String ref, UpdateSkillRequest request) {

        requireNonBlank(request.name(),        "name");
        requireNonBlank(request.instructions(), "instructions");

        var existing = resolve(ref);
        if (existing == null) throw new NotFoundException("No skill with id or name: " + ref);

        var beforeJson = toJson(existing);

        var cfg = new SkillConfig(existing.id(), request.name(), request.instructions());
        agentican.registry().skills().register(cfg);

        audit.record(CatalogAuditLog.SKILL, existing.id(), CatalogAuditLog.UPDATED,
                null, beforeJson, toJson(cfg));

        return SkillSummary.of(resolve(existing.id()), false);
    }

    @DELETE
    @Path("/{ref}")
    public Response delete(@PathParam("ref") String ref) {

        var existing = resolve(ref);
        if (existing == null) throw new NotFoundException("No skill with id or name: " + ref);

        var referring = referringPlans(existing);
        if (!referring.isEmpty())
            throw new CatalogConflictException("referenced",
                    "Skill '" + existing.name() + "' is referenced by " + referring.size() + " definition(s)",
                    referring);

        var beforeJson = toJson(existing);

        agentican.registry().skills().delete(ref);

        audit.record(CatalogAuditLog.SKILL, existing.id(), CatalogAuditLog.DELETED,
                null, beforeJson, null);

        return Response.noContent().build();
    }

    private String toJson(Object value) {

        try {
            return objectMapper.writeValueAsString(value);
        }
        catch (Exception e) {
            return null;
        }
    }

    private SkillConfig resolve(String ref) {

        var skills = agentican.registry().skills();

        var byId = skills.byId(ref);
        if (byId != null) return byId;

        return skills.byName(ref);
    }

    private Set<String> configDeclaredNames() {

        var out = new HashSet<String>();
        catalogConfig.skills().forEach(s -> out.add(s.name()));
        return out;
    }

    private List<String> referringPlans(SkillConfig skill) {

        var plans = agentican.registry().workflows();

        var refs = Set.of(skill.id(), skill.name());

        var hits = new HashSet<String>();
        for (var ref : refs)
            hits.addAll(CatalogReferences.plansReferencingSkill(plans, ref));

        return List.copyOf(hits);
    }

    private static void requireNonBlank(String value, String field) {

        if (value == null || value.isBlank())
            throw new BadRequestException(field + " is required");
    }
}
