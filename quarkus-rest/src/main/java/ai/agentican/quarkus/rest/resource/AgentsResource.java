package ai.agentican.quarkus.rest.resource;

import ai.agentican.framework.Agentican;
import ai.agentican.framework.agent.Agent;
import ai.agentican.framework.config.AgentConfig;
import ai.agentican.framework.util.Ids;
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
import ai.agentican.framework.config.CatalogConfig;

@Path("/agentican/agents")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AgentsResource {

    @Inject
    Agentican agentican;

    @Inject
    CatalogConfig catalogConfig;

    @Inject
    CatalogAuditLog audit;

    @Inject
    ObjectMapper objectMapper;

    @GET
    public List<AgentSummary> list() {

        var configNames = configDeclaredNames();

        return agentican.registry().agents().list().stream()
                .map(a -> AgentSummary.of(a, configNames.contains(a.name())))
                .toList();
    }

    @GET
    @Path("/{ref}")
    public AgentSummary get(@PathParam("ref") String ref) {

        var agent = resolve(ref);
        if (agent == null) throw new NotFoundException("No agent with id or name: " + ref);

        return AgentSummary.of(agent, configDeclaredNames().contains(agent.name()));
    }

    @POST
    public Response create(CreateAgentRequest request) {

        requireNonBlank(request.name(), "name");
        requireNonBlank(request.role(), "role");

        var agents = agentican.registry().agents();
        if (agents.byName(request.name()) != null)
            throw new CatalogConflictException("already_exists",
                    "Agent with name '" + request.name() + "' already exists");

        var cfg = new AgentConfig(Ids.generate(), request.name(), request.role(), request.llm(), null, null, null);
        agentican.registry().agents().register(cfg);

        var created = agents.byName(request.name());
        audit.record(CatalogAuditLog.AGENT, created.id(), CatalogAuditLog.CREATED,
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
        if (existing == null) throw new NotFoundException("No agent with id or name: " + ref);

        var beforeJson = toJson(existing.config());

        var cfg = new AgentConfig(
                existing.id(),
                request.name(),
                request.role(),
                request.llm() != null ? request.llm() : existing.config().llm(),
                existing.config().runner(),
                existing.config().maxTurns(),
                existing.config().timeout());

        agentican.registry().agents().register(cfg);

        audit.record(CatalogAuditLog.AGENT, existing.id(), CatalogAuditLog.UPDATED,
                null, beforeJson, toJson(cfg));

        return AgentSummary.of(resolve(existing.id()), false);
    }

    @DELETE
    @Path("/{ref}")
    public Response delete(@PathParam("ref") String ref) {

        var existing = resolve(ref);
        if (existing == null) throw new NotFoundException("No agent with id or name: " + ref);

        var referring = referringPlans(existing);
        if (!referring.isEmpty())
            throw new CatalogConflictException("referenced",
                    "Agent '" + existing.name() + "' is referenced by " + referring.size() + " definition(s)",
                    referring);

        var beforeJson = toJson(existing.config());

        agentican.registry().agents().delete(ref);

        audit.record(CatalogAuditLog.AGENT, existing.id(), CatalogAuditLog.DELETED,
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

        var byId = agents.byId(ref);
        if (byId != null) return byId;

        return agents.byName(ref);
    }

    private Set<String> configDeclaredNames() {

        var out = new HashSet<String>();
        catalogConfig.agents().forEach(a -> out.add(a.name()));
        return out;
    }

    private List<String> referringPlans(Agent agent) {

        var plans = agentican.registry().workflows();

        var refs = Set.of(agent.id(), agent.name());

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
