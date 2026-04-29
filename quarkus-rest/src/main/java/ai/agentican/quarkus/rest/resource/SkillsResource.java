package ai.agentican.quarkus.rest.resource;

import ai.agentican.framework.Agentican;
import ai.agentican.framework.config.SkillConfig;
import ai.agentican.framework.util.Ids;
import ai.agentican.quarkus.AgenticanConfig;
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
    AgenticanConfig config;

    @Inject
    CatalogAuditLog audit;

    @Inject
    ObjectMapper objectMapper;

    @GET
    public List<SkillSummary> list() {

        var propertyIds = propertyDeclaredExternalIds();

        return agentican.registry().skills().getAll().stream()
                .map(s -> SkillSummary.of(s, isPropertyDeclared(s, propertyIds)))
                .toList();
    }

    @GET
    @Path("/{ref}")
    public SkillSummary get(@PathParam("ref") String ref) {

        var skill = resolve(ref);
        if (skill == null) throw new NotFoundException("No skill with id, externalId or name: " + ref);

        return SkillSummary.of(skill, isPropertyDeclared(skill, propertyDeclaredExternalIds()));
    }

    @POST
    public Response create(CreateSkillRequest request) {

        requireNonBlank(request.externalId(),  "externalId");
        requireNonBlank(request.name(),        "name");
        requireNonBlank(request.instructions(), "instructions");

        if (propertyDeclaredExternalIds().contains(request.externalId()))
            throw new CatalogConflictException("property_declared",
                    "externalId '" + request.externalId() + "' is declared in application.properties and is read-only");

        var skills = agentican.registry().skills();
        if (skills.getByExternalId(request.externalId()) != null)
            throw new CatalogConflictException("already_exists",
                    "Skill with externalId '" + request.externalId() + "' already exists");

        var cfg = new SkillConfig(Ids.generate(), request.name(), request.instructions(), request.externalId());
        skills.register(cfg);

        var created = skills.getByExternalId(request.externalId());
        audit.record(CatalogAuditLog.SKILL, request.externalId(), CatalogAuditLog.CREATED,
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
        if (existing == null) throw new NotFoundException("No skill with id, externalId or name: " + ref);

        if (isPropertyDeclared(existing, propertyDeclaredExternalIds()))
            throw new CatalogConflictException("property_declared",
                    "Skill '" + existing.name() + "' is declared in application.properties and is read-only");

        var beforeJson = toJson(existing);

        var cfg = new SkillConfig(existing.id(), request.name(), request.instructions(), existing.externalId());
        agentican.registry().skills().register(cfg);

        var key = existing.externalId() != null ? existing.externalId() : existing.id();
        audit.record(CatalogAuditLog.SKILL, key, CatalogAuditLog.UPDATED,
                null, beforeJson, toJson(cfg));

        return SkillSummary.of(resolve(key), false);
    }

    @DELETE
    @Path("/{ref}")
    public Response delete(@PathParam("ref") String ref) {

        var existing = resolve(ref);
        if (existing == null) throw new NotFoundException("No skill with id, externalId or name: " + ref);

        if (isPropertyDeclared(existing, propertyDeclaredExternalIds()))
            throw new CatalogConflictException("property_declared",
                    "Skill '" + existing.name() + "' is declared in application.properties and is read-only");

        var referring = referringPlans(existing);
        if (!referring.isEmpty())
            throw new CatalogConflictException("referenced",
                    "Skill '" + existing.name() + "' is referenced by " + referring.size() + " plan(s)",
                    referring);

        var beforeJson = toJson(existing);

        agentican.registry().skills().delete(ref);

        var key = existing.externalId() != null ? existing.externalId() : existing.id();
        audit.record(CatalogAuditLog.SKILL, key, CatalogAuditLog.DELETED,
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

        var byExt = skills.getByExternalId(ref);
        if (byExt != null) return byExt;

        var byId = skills.get(ref);
        if (byId != null) return byId;

        return skills.getByName(ref);
    }

    private Set<String> propertyDeclaredExternalIds() {

        var out = new HashSet<String>();
        config.skills().forEach(s -> s.externalId().ifPresent(out::add));
        return out;
    }

    private static boolean isPropertyDeclared(SkillConfig skill, Set<String> propertyIds) {

        var ext = skill.externalId();
        return ext != null && propertyIds.contains(ext);
    }

    private List<String> referringPlans(SkillConfig skill) {

        var plans = agentican.registry().plans();

        var refs = new HashSet<String>();
        refs.add(skill.id());
        refs.add(skill.name());
        if (skill.externalId() != null) refs.add(skill.externalId());

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
