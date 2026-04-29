package ai.agentican.quarkus.rest.error;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import java.util.List;

@Provider
public class CatalogConflictExceptionMapper implements ExceptionMapper<CatalogConflictException> {

    @Override
    public Response toResponse(CatalogConflictException e) {

        return Response.status(Response.Status.CONFLICT)
                .entity(new Body(e.code(), e.getMessage(), e.referring()))
                .type(MediaType.APPLICATION_JSON)
                .build();
    }

    public record Body(String code, String message, List<String> referring) {}
}
