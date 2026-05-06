package ai.agentican.quarkus.rest.resource;

import ai.agentican.framework.Agentican;
import ai.agentican.framework.orchestration.model.WorkflowDefinition;
import ai.agentican.framework.orchestration.model.WorkflowDefinitionValidator;
import ai.agentican.framework.util.Ids;
import ai.agentican.quarkus.audit.CatalogAuditLog;
import ai.agentican.quarkus.rest.dto.WorkflowView;
import ai.agentican.quarkus.rest.dto.UpsertPlanRequest;
import ai.agentican.quarkus.rest.error.CatalogConflictException;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;

@Path("/agentican/plans")
@Produces(MediaType.APPLICATION_JSON)
public class WorkflowsResource {

    private static final String APPLICATION_YAML = "application/yaml";
    private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory());

    @Inject
    Agentican agentican;

    @Inject
    CatalogAuditLog audit;

    @Inject
    ObjectMapper objectMapper;

    @GET
    public List<WorkflowView> list() {

        return agentican.registry().workflows().list().stream()
                .map(WorkflowView::of)
                .toList();
    }

    @GET
    @Path("/{ref}")
    public WorkflowView get(@PathParam("ref") String ref) {

        var plan = resolve(ref);
        if (plan == null) throw new NotFoundException("No definition with id or name: " + ref);

        return WorkflowView.of(plan);
    }

    @POST
    @Consumes({MediaType.APPLICATION_JSON, APPLICATION_YAML, "text/yaml", "application/x-yaml"})
    public Response create(String body, @HeaderParam("Content-Type") String contentType) {

        var request = parseBody(body, contentType);
        var plan = extractPlan(request);

        if (agentican.registry().workflows().hasByName(plan.name()))
            throw new CatalogConflictException("already_exists",
                    "WorkflowDefinition with name '" + plan.name() + "' already exists");

        var issues = WorkflowDefinitionValidator.validate(plan,
                agentican.registry().agents(), agentican.registry().skills());
        if (!issues.isEmpty())
            throw new CatalogConflictException("invalid_plan",
                    "WorkflowDefinition failed validation: " + issues.size() + " issue(s)", issues);

        var saved = agentican.registry().workflows().register(plan);
        audit.record(CatalogAuditLog.PLAN, saved.id(), CatalogAuditLog.CREATED,
                null, null, toJson(saved));

        return Response.status(Response.Status.CREATED)
                .entity(WorkflowView.of(saved))
                .build();
    }

    @PUT
    @Path("/{ref}")
    @Consumes({MediaType.APPLICATION_JSON, APPLICATION_YAML, "text/yaml", "application/x-yaml"})
    public WorkflowView update(@PathParam("ref") String ref, String body,
                            @HeaderParam("Content-Type") String contentType) {

        var existing = resolve(ref);
        if (existing == null) throw new NotFoundException("No definition with id or name: " + ref);

        var request = parseBody(body, contentType);
        var incoming = extractPlan(request);

        var aligned = new WorkflowDefinition(
                existing.id(),
                incoming.name() != null ? incoming.name() : existing.name(),
                incoming.description() != null ? incoming.description() : existing.description(),
                incoming.params() != null ? incoming.params() : existing.params(),
                incoming.steps() != null ? incoming.steps() : existing.steps(),
                incoming.outputStep() != null ? incoming.outputStep() : existing.outputStep());

        var issues = WorkflowDefinitionValidator.validate(aligned,
                agentican.registry().agents(), agentican.registry().skills());
        if (!issues.isEmpty())
            throw new CatalogConflictException("invalid_plan",
                    "WorkflowDefinition failed validation: " + issues.size() + " issue(s)", issues);

        var beforeJson = toJson(existing);
        var saved = agentican.registry().workflows().register(aligned);

        audit.record(CatalogAuditLog.PLAN, saved.id(),
                CatalogAuditLog.UPDATED, null, beforeJson, toJson(saved));

        return WorkflowView.of(saved);
    }

    @DELETE
    @Path("/{ref}")
    public Response delete(@PathParam("ref") String ref) {

        var existing = resolve(ref);
        if (existing == null) throw new NotFoundException("No definition with id or name: " + ref);

        var beforeJson = toJson(existing);

        agentican.registry().workflows().delete(ref);

        audit.record(CatalogAuditLog.PLAN, existing.id(),
                CatalogAuditLog.DELETED, null, beforeJson, null);

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

    private UpsertPlanRequest parseBody(String body, String contentType) {

        if (body == null || body.isBlank())
            throw new BadRequestException("Request body is empty");

        var mapper = isYaml(contentType) ? YAML : objectMapper;
        try {
            return mapper.readValue(body, UpsertPlanRequest.class);
        }
        catch (Exception e) {
            throw new BadRequestException("Failed to parse " + (isYaml(contentType) ? "YAML" : "JSON")
                    + " body: " + e.getMessage());
        }
    }

    private static boolean isYaml(String contentType) {

        if (contentType == null) return false;
        return contentType.toLowerCase().contains("yaml");
    }

    private WorkflowDefinition resolve(String ref) {

        var plans = agentican.registry().workflows();

        var byId = plans.byId(ref);
        if (byId != null) return byId;

        return plans.byName(ref);
    }

    private static WorkflowDefinition extractPlan(UpsertPlanRequest request) {

        if (request == null || request.definition() == null)
            throw new BadRequestException("definition is required");

        var src = request.definition();
        var id = (src.id() == null || src.id().isBlank()) ? Ids.generate() : src.id();

        return new WorkflowDefinition(id, src.name(), src.description(), src.params(), src.steps(), src.outputStep());
    }
}
