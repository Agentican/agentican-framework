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
    ai.agentican.framework.config.EngineConfig engineConfig;

    @Inject
    ai.agentican.framework.config.CatalogConfig catalogConfig;

    @Override
    public HealthCheckResponse call() {

        var builder = HealthCheckResponse.named("agentican-readiness");

        if (agentican == null)
            return builder.down().withData("reason", "Agentican bean not initialized").build();

        if (engineConfig.llm().isEmpty())
            return builder.down().withData("reason", "No LLMs configured").build();

        var registered = agentican.registry().agents().asMap().size();
        var declared = catalogConfig.agents().size();

        if (registered < declared) {

            return builder.down()
                    .withData("reason", "Configured agents not all registered")
                    .withData("declared", declared)
                    .withData("registered", registered)
                    .build();
        }

        return builder.up()
                .withData("llms", engineConfig.llm().size())
                .withData("agents", registered)
                .build();
    }
}
