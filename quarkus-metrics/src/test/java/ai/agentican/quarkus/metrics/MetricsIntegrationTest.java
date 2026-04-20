package ai.agentican.quarkus.metrics;

import ai.agentican.framework.AgenticanRuntime;
import ai.agentican.framework.orchestration.model.Plan;
import ai.agentican.framework.orchestration.model.PlanStepAgent;
import ai.agentican.quarkus.test.MockLlmClient;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;

@QuarkusTest
class MetricsIntegrationTest {

    @Inject
    AgenticanRuntime agentican;

    @Inject
    MockLlmClient mockLlm;

    @BeforeEach
    void resetMock() {

        mockLlm.reset();
    }

    @Test
    void lifecycleMetricsAppearAfterTaskExecution() {

        mockLlm.queueEndTurn("Test result");

        var step = new PlanStepAgent("research", "researcher", "do something",
                List.of(), false, List.of(), List.of());
        var task = Plan.builder("metrics-test").description("test").step(step).build();

        var handle = agentican.run(task);
        handle.result();

        given()
                .when().get("/q/metrics")
                .then()
                .statusCode(200)
                .body(containsString("agentican_tasks_completed_total"))
                .body(containsString("agentican_tasks_duration_seconds"))
                .body(containsString("agentican_steps_completed_total"))
                .body(containsString("agentican_tasks_active"));
    }

    @Test
    void gaugesAreRegisteredAtStartup() {

        given()
                .when().get("/q/metrics")
                .then()
                .statusCode(200)
                .body(containsString("agentican_tasks_active"))
                .body(containsString("agentican_hitl_checkpoints_pending"));
    }
}
