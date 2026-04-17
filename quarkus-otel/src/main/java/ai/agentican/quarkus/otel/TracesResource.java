package ai.agentican.quarkus.otel;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import java.util.List;

@Path("/agentican/traces")
@Produces(MediaType.APPLICATION_JSON)
public class TracesResource {

    @Inject
    SpanStore spanStore;

    @GET
    @Path("/{taskId}")
    public List<SpanView> getTrace(@PathParam("taskId") String taskId) {

        var spans = spanStore.getByTaskId(taskId);

        if (spans.isEmpty())
            throw new NotFoundException("No trace found for task: " + taskId);

        return spans;
    }
}
