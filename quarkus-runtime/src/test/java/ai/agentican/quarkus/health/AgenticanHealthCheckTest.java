package ai.agentican.quarkus.health;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Liveness;
import org.eclipse.microprofile.health.Readiness;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class AgenticanHealthCheckTest {

    @Inject
    @Liveness
    HealthCheck liveness;

    @Inject
    @Readiness
    HealthCheck readiness;

    @Test
    void livenessReportsUpWhenAgenticanIsInitialized() {

        var response = liveness.call();

        assertEquals("agentican", response.getName());
        assertEquals(HealthCheckResponse.Status.UP, response.getStatus());
    }

    @Test
    void readinessReportsUpWhenLlmAndAgentsAreConfigured() {

        var response = readiness.call();

        assertEquals("agentican-readiness", response.getName());
        assertEquals(HealthCheckResponse.Status.UP, response.getStatus());

        assertTrue(response.getData().isPresent());
        assertEquals(1L, response.getData().get().get("llms"));
        assertEquals(1L, response.getData().get().get("agents"));
    }
}
