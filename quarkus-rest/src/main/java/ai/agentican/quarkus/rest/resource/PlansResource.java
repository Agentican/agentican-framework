package ai.agentican.quarkus.rest.resource;

import ai.agentican.framework.AgenticanRuntime;
import ai.agentican.quarkus.rest.dto.PlanView;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import java.util.List;

@Path("/agentican/plans")
@Produces(MediaType.APPLICATION_JSON)
public class PlansResource {

    @Inject
    AgenticanRuntime agentican;

    @GET
    public List<PlanView> list() {

        return agentican.registry().plans().getAll().stream()
                .map(PlanView::of)
                .toList();
    }

    @GET
    @Path("/{planId}")
    public PlanView get(@PathParam("planId") String planId) {

        var plan = agentican.registry().plans().getById(planId);

        if (plan == null)
            throw new NotFoundException("No plan definition with id: " + planId);

        return PlanView.of(plan);
    }
}
