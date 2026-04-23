package ai.agentican.quarkus.store.rest;

import ai.agentican.quarkus.store.rest.dto.RestAgentView;
import ai.agentican.quarkus.store.rest.dto.RestSkillView;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import java.util.List;

@Path("/agentican")
@Produces(MediaType.APPLICATION_JSON)
@RegisterRestClient(configKey = "agentican-catalog")
public interface RestCatalogClient {

    // Plans are returned as raw JSON (String) so the registry can
    // deserialize with a PlanCodec-aware reader — code steps carry
    // typed I inputs that need the CodeStepRegistry for reconstruction.

    @GET
    @Path("/plans")
    String listPlansJson();

    @GET
    @Path("/plans/{id}")
    String getPlanJson(@PathParam("id") String id);

    @GET
    @Path("/agents")
    List<RestAgentView> listAgents();

    @GET
    @Path("/agents/{ref}")
    RestAgentView getAgent(@PathParam("ref") String ref);

    @GET
    @Path("/skills")
    List<RestSkillView> listSkills();

    @GET
    @Path("/skills/{ref}")
    RestSkillView getSkill(@PathParam("ref") String ref);
}
