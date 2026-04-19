package ai.agentican.quarkus.rest.resource;

import ai.agentican.framework.Agentican;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import java.util.List;

@Path("/agentican/tools")
@Produces(MediaType.APPLICATION_JSON)
public class ToolsResource {

    @Inject
    Agentican agentican;

    public record ToolkitView(String slug, String displayName, List<ToolView> tools) {}
    public record ToolView(String name, String displayName, String description) {}

    @GET
    public List<ToolkitView> list() {

        var registry = agentican.registry().toolkits();

        return registry.slugs().stream().map(slug -> {

            var toolkit = registry.get(slug);
            var tools = toolkit.tools().stream()
                    .map(t -> new ToolView(t.name(), t.displayName(), t.description()))
                    .toList();

            return new ToolkitView(slug, toolkit.displayName(), tools);
        }).toList();
    }
}
