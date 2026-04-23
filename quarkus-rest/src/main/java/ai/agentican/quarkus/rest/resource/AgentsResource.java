package ai.agentican.quarkus.rest.resource;

import ai.agentican.framework.Agentican;
import ai.agentican.quarkus.rest.dto.AgentSummary;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import java.util.List;

@Path("/agentican/agents")
@Produces(MediaType.APPLICATION_JSON)
public class AgentsResource {

    @Inject
    Agentican agentican;

    @GET
    public List<AgentSummary> list() {

        return agentican.registry().agents().getAll().stream()
                .map(AgentSummary::of)
                .toList();
    }

    @GET
    @Path("/{ref}")
    public AgentSummary get(@PathParam("ref") String ref) {

        var agent = agentican.registry().agents().get(ref);

        if (agent == null) agent = agentican.registry().agents().getByName(ref);

        if (agent == null)
            throw new NotFoundException("No agent with id or name: " + ref);

        return AgentSummary.of(agent);
    }
}
