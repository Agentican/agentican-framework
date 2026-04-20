package ai.agentican.quarkus.health;

import ai.agentican.framework.AgenticanRuntime;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Liveness;

@Liveness
@ApplicationScoped
public class AgenticanLivenessCheck implements HealthCheck {

    @Inject
    AgenticanRuntime agentican;

    @Override
    public HealthCheckResponse call() {

        var builder = HealthCheckResponse.named("agentican");

        if (agentican == null)
            return builder.down().withData("reason", "AgenticanRuntime bean not initialized").build();

        return builder.up().build();
    }
}
