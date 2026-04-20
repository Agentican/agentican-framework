package ai.agentican.quarkus.rest.resource;

import ai.agentican.framework.knowledge.KnowledgeFact;
import ai.agentican.framework.knowledge.KnowledgeEntry;
import ai.agentican.framework.knowledge.KnowledgeStatus;
import ai.agentican.framework.store.KnowledgeStore;
import ai.agentican.quarkus.rest.dto.AddFactRequest;
import ai.agentican.quarkus.rest.dto.CreateKnowledgeRequest;
import ai.agentican.quarkus.rest.dto.KnowledgeEntrySummary;
import ai.agentican.quarkus.rest.dto.KnowledgeEntryView;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;

import java.util.List;

@Path("/agentican/knowledge")
@Produces(MediaType.APPLICATION_JSON)
@RunOnVirtualThread
public class KnowledgeResource {

    @Inject
    KnowledgeStore knowledgeStore;

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public Response create(CreateKnowledgeRequest request, @Context UriInfo uriInfo) {

        if (request == null || request.name() == null || request.name().isBlank())
            throw new BadRequestException("name is required");

        var entry = KnowledgeEntry.of(request.name(), request.description());
        entry.setStatus(KnowledgeStatus.INDEXED);
        knowledgeStore.save(entry);

        var location = uriInfo.getAbsolutePathBuilder().path(entry.id()).build();

        return Response.created(location)
                .entity(KnowledgeEntryView.of(entry))
                .build();
    }

    @GET
    public List<KnowledgeEntrySummary> list() {

        return knowledgeStore.all().stream()
                .map(KnowledgeEntrySummary::of)
                .toList();
    }

    @GET
    @Path("/{id}")
    public KnowledgeEntryView get(@PathParam("id") String id) {

        return KnowledgeEntryView.of(loadOrThrow(id));
    }

    @DELETE
    @Path("/{id}")
    public Response delete(@PathParam("id") String id) {

        if (knowledgeStore.get(id) == null)
            throw new NotFoundException("No knowledge entry with id: " + id);

        knowledgeStore.delete(id);

        return Response.noContent().build();
    }

    @POST
    @Path("/{id}/facts")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response addFact(@PathParam("id") String id, AddFactRequest request) {

        if (request == null || request.name() == null || request.name().isBlank())
            throw new BadRequestException("fact name is required");

        var entry = loadOrThrow(id);

        var tags = request.tags() != null ? request.tags() : List.<String>of();
        var content = request.content() != null ? request.content() : "";

        var fact = KnowledgeFact.of(request.name(), content, tags);
        entry.addFact(fact);
        knowledgeStore.save(entry);

        return Response.ok(KnowledgeEntryView.KnowledgeFactView.of(fact)).build();
    }

    private KnowledgeEntry loadOrThrow(String id) {

        var entry = knowledgeStore.get(id);

        if (entry == null)
            throw new NotFoundException("No knowledge entry with id: " + id);

        return entry;
    }
}
