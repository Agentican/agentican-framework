package ai.agentican.quarkus.rest.error;

import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class NotFoundExceptionMapper implements ExceptionMapper<NotFoundException> {

    @Override
    public Response toResponse(NotFoundException exception) {

        return Response.status(Response.Status.NOT_FOUND)
                .entity(new RestError("not_found", exception.getMessage()))
                .type(MediaType.APPLICATION_JSON)
                .build();
    }
}
