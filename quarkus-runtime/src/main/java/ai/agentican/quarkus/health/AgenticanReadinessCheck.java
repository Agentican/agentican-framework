package ai.agentican.quarkus.health;

import ai.agentican.framework.Agentican;
import ai.agentican.quarkus.AgenticanConfig;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Readiness;

@Readiness
@ApplicationScoped
public class AgenticanReadinessCheck implements HealthCheck {

    @Inject
    Agentican agentican;

    @Inject
    AgenticanConfig config;

    @Override
    public HealthCheckResponse call() {

        var builder = HealthCheckResponse.named("agentican-readiness");

        if (agentican == null)
            return builder.down().withData("reason", "Agentican bean not initialized").build();

        if (config.llm().isEmpty())
            return builder.down().withData("reason", "No agentican.llm[*] entries configured").build();

        for (var agentConfig : config.agents()) {

            var byId = agentConfig.id().map(id -> agentican.agents().get(id)).orElse(null);
            var byName = agentican.agents().getByName(agentConfig.name());

            if (byId == null && byName == null) {

                return builder.down()
                        .withData("reason", "Configured agent not registered: " + agentConfig.name())
                        .build();
            }
        }

        return builder.up()
                .withData("llms", config.llm().size())
                .withData("agents", config.agents().size())
                .build();
    }
}
