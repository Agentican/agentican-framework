package ai.agentican.quarkus.rest.resource;

import ai.agentican.framework.hitl.HitlCheckpoint;
import ai.agentican.framework.hitl.HitlManager;
import ai.agentican.framework.hitl.HitlResponse;
import ai.agentican.quarkus.rest.TaskEventBus;
import ai.agentican.quarkus.rest.dto.HitlResponseDto;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;
import java.util.Map;

@Path("/agentican/checkpoints")
@Produces(MediaType.APPLICATION_JSON)
@RunOnVirtualThread
public class CheckpointsResource {

    @Inject
    HitlManager hitlManager;

    @Inject
    TaskEventBus eventBus;

    @GET
    public Map<String, List<HitlCheckpoint>> listAll() {

        return eventBus.allPending();
    }

    @GET
    @Path("/{taskId}")
    public List<HitlCheckpoint> listForTask(@PathParam("taskId") String taskId) {

        return eventBus.pendingFor(taskId);
    }

    @POST
    @Path("/{checkpointId}/respond")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response respond(@PathParam("checkpointId") String checkpointId, HitlResponseDto body) {

        if (body == null)
            throw new jakarta.ws.rs.BadRequestException("Request body is required");

        if (!hitlManager.pendingCheckpoints().containsKey(checkpointId))
            throw new NotFoundException("No pending checkpoint with id: " + checkpointId);

        hitlManager.respond(checkpointId, new HitlResponse(body.approved(), body.feedback()));
        eventBus.clearCheckpoint(checkpointId);

        return Response.noContent().build();
    }

    @POST
    @Path("/{checkpointId}/cancel")
    public Response cancel(@PathParam("checkpointId") String checkpointId) {

        if (!hitlManager.pendingCheckpoints().containsKey(checkpointId))
            throw new NotFoundException("No pending checkpoint with id: " + checkpointId);

        hitlManager.cancel(checkpointId);
        eventBus.clearCheckpoint(checkpointId);

        return Response.noContent().build();
    }
}
