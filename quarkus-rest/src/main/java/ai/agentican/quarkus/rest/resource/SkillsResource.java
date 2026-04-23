package ai.agentican.quarkus.rest.resource;

import ai.agentican.framework.Agentican;
import ai.agentican.quarkus.rest.dto.SkillSummary;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import java.util.List;

@Path("/agentican/skills")
@Produces(MediaType.APPLICATION_JSON)
public class SkillsResource {

    @Inject
    Agentican agentican;

    @GET
    public List<SkillSummary> list() {

        return agentican.registry().skills().getAll().stream()
                .map(SkillSummary::of)
                .toList();
    }

    @GET
    @Path("/{ref}")
    public SkillSummary get(@PathParam("ref") String ref) {

        var skill = agentican.registry().skills().get(ref);

        if (skill == null) skill = agentican.registry().skills().getByName(ref);

        if (skill == null)
            throw new NotFoundException("No skill with id or name: " + ref);

        return SkillSummary.of(skill);
    }
}
