package ai.agentican.quarkus.rest.resource;

import ai.agentican.framework.Agentican;
import ai.agentican.framework.agent.Agent;
import ai.agentican.framework.config.AgentConfig;
import ai.agentican.framework.util.Ids;
import ai.agentican.quarkus.AgenticanConfig;
import ai.agentican.quarkus.audit.CatalogAuditLog;
import ai.agentican.quarkus.rest.catalog.CatalogReferences;
import ai.agentican.quarkus.rest.dto.AgentSummary;
import ai.agentican.quarkus.rest.dto.CreateAgentRequest;
import ai.agentican.quarkus.rest.dto.UpdateAgentRequest;
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

@Path("/agentican/agents")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AgentsResource {

    @Inject
    Agentican agentican;

    @Inject
    AgenticanConfig config;

    @Inject
    CatalogAuditLog audit;

    @Inject
    ObjectMapper objectMapper;

    @GET
    public List<AgentSummary> list() {

        var propertyIds = propertyDeclaredExternalIds();

        return agentican.registry().agents().getAll().stream()
                .map(a -> AgentSummary.of(a, isPropertyDeclared(a, propertyIds)))
                .toList();
    }

    @GET
    @Path("/{ref}")
    public AgentSummary get(@PathParam("ref") String ref) {

        var agent = resolve(ref);
        if (agent == null) throw new NotFoundException("No agent with id, externalId or name: " + ref);

        return AgentSummary.of(agent, isPropertyDeclared(agent, propertyDeclaredExternalIds()));
    }

    @POST
    public Response create(CreateAgentRequest request) {

        requireNonBlank(request.externalId(), "externalId");
        requireNonBlank(request.name(),       "name");
        requireNonBlank(request.role(),       "role");

        if (propertyDeclaredExternalIds().contains(request.externalId()))
            throw new CatalogConflictException("property_declared",
                    "externalId '" + request.externalId() + "' is declared in application.properties and is read-only");

        var agents = agentican.registry().agents();
        if (agents.getByExternalId(request.externalId()) != null)
            throw new CatalogConflictException("already_exists",
                    "Agent with externalId '" + request.externalId() + "' already exists");

        var cfg = new AgentConfig(Ids.generate(), request.name(), request.role(),
                request.llm(), request.externalId());
        agents.register(agentican.buildAgent(cfg));

        var created = agents.getByExternalId(request.externalId());
        audit.record(CatalogAuditLog.AGENT, request.externalId(), CatalogAuditLog.CREATED,
                null, null, toJson(cfg));

        return Response.status(Response.Status.CREATED)
                .entity(AgentSummary.of(created, false))
                .build();
    }

    @PUT
    @Path("/{ref}")
    public AgentSummary update(@PathParam("ref") String ref, UpdateAgentRequest request) {

        requireNonBlank(request.name(), "name");
        requireNonBlank(request.role(), "role");

        var existing = resolve(ref);
        if (existing == null) throw new NotFoundException("No agent with id, externalId or name: " + ref);

        if (isPropertyDeclared(existing, propertyDeclaredExternalIds()))
            throw new CatalogConflictException("property_declared",
                    "Agent '" + existing.name() + "' is declared in application.properties and is read-only");

        var beforeJson = toJson(existing.config());

        var cfg = new AgentConfig(
                existing.id(),
                request.name(),
                request.role(),
                request.llm() != null ? request.llm() : existing.config().llm(),
                existing.config().externalId());

        agentican.registry().agents().register(agentican.buildAgent(cfg));

        var key = existing.config().externalId() != null ? existing.config().externalId() : existing.id();
        audit.record(CatalogAuditLog.AGENT, key, CatalogAuditLog.UPDATED,
                null, beforeJson, toJson(cfg));

        return AgentSummary.of(resolve(key), false);
    }

    @DELETE
    @Path("/{ref}")
    public Response delete(@PathParam("ref") String ref) {

        var existing = resolve(ref);
        if (existing == null) throw new NotFoundException("No agent with id, externalId or name: " + ref);

        if (isPropertyDeclared(existing, propertyDeclaredExternalIds()))
            throw new CatalogConflictException("property_declared",
                    "Agent '" + existing.name() + "' is declared in application.properties and is read-only");

        var referring = referringPlans(existing);
        if (!referring.isEmpty())
            throw new CatalogConflictException("referenced",
                    "Agent '" + existing.name() + "' is referenced by " + referring.size() + " plan(s)",
                    referring);

        var beforeJson = toJson(existing.config());

        agentican.registry().agents().delete(ref);

        var key = existing.config().externalId() != null ? existing.config().externalId() : existing.id();
        audit.record(CatalogAuditLog.AGENT, key, CatalogAuditLog.DELETED,
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

    private Agent resolve(String ref) {

        var agents = agentican.registry().agents();

        var byExt = agents.getByExternalId(ref);
        if (byExt != null) return byExt;

        var byId = agents.get(ref);
        if (byId != null) return byId;

        return agents.getByName(ref);
    }

    private Set<String> propertyDeclaredExternalIds() {

        var out = new HashSet<String>();
        config.agents().forEach(a -> a.externalId().ifPresent(out::add));
        return out;
    }

    private static boolean isPropertyDeclared(Agent agent, Set<String> propertyIds) {

        var ext = agent.config().externalId();
        return ext != null && propertyIds.contains(ext);
    }

    private List<String> referringPlans(Agent agent) {

        var plans = agentican.registry().plans();

        var refs = new HashSet<String>();
        refs.add(agent.id());
        refs.add(agent.name());
        if (agent.config().externalId() != null) refs.add(agent.config().externalId());

        var hits = new HashSet<String>();
        for (var ref : refs)
            hits.addAll(CatalogReferences.plansReferencingAgent(plans, ref));

        return List.copyOf(hits);
    }

    private static void requireNonBlank(String value, String field) {

        if (value == null || value.isBlank())
            throw new BadRequestException(field + " is required");
    }
}
