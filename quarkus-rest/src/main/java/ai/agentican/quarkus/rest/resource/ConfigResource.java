package ai.agentican.quarkus.rest.resource;

import ai.agentican.framework.AgenticanRuntime;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import java.util.ArrayList;
import java.util.List;

@Path("/agentican/config")
@Produces(MediaType.APPLICATION_JSON)
public class ConfigResource {

    @Inject
    AgenticanRuntime agentican;

    @Inject
    org.eclipse.microprofile.config.Config mpConfig;

    public record ConfigProperty(String name, String value) {}

    @GET
    public List<ConfigProperty> get() {

        var props = new ArrayList<ConfigProperty>();

        addQuarkusProperties(props);

        return props;
    }

    private void addQuarkusProperties(List<ConfigProperty> props) {

        addIfPresent(props, "quarkus.otel.sdk.disabled");
        addIfPresent(props, "quarkus.otel.bsp.schedule.delay");
        addIfPresent(props, "quarkus.http.port");
        addIfPresent(props, "quarkus.http.cors.enabled");

        for (var name : mpConfig.getPropertyNames()) {

            if (name.startsWith("agentican.") && !name.contains("api-key") && !name.contains("apiKey")
                    && !name.startsWith("agentican.agents")) {
                try {
                    var value = mpConfig.getValue(name, String.class);
                    props.add(new ConfigProperty(name, value));
                } catch (Exception ignored) {}
            }
        }

        props.sort((a, b) -> a.name().compareTo(b.name()));
    }

    private void addIfPresent(List<ConfigProperty> props, String name) {

        mpConfig.getOptionalValue(name, String.class)
                .ifPresent(value -> props.add(new ConfigProperty(name, value)));
    }
}
