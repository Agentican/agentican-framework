package ai.agentican.quarkus.rest.resource;

import ai.agentican.framework.orchestration.model.PlanStepAgent;
import ai.agentican.framework.orchestration.model.Plan;
import ai.agentican.quarkus.test.MockLlmClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static io.restassured.RestAssured.given;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

@QuarkusTest
class EndToEndTaskTest {

    @Inject
    MockLlmClient mockLlm;

    @Inject
    ObjectMapper objectMapper;

    @BeforeEach
    void resetMock() {

        mockLlm.reset();
    }

    @Test
    void submitPreBuiltTaskAndVerifyCompletion() throws Exception {

        mockLlm.queueEndTurn("Here is the research result about agent frameworks.");

        var step = PlanStepAgent.of("research", "researcher", "Find papers on agents",
                List.of(), false, List.of(), List.of());
        var task = Plan.of("e2e-prebuilt", "E2E test task", List.of(), List.of(step));
        var taskJson = objectMapper.writeValueAsString(task);

        var taskId = given()
                .contentType("application/json")
                .body("{\"task\": " + taskJson + "}")
                .when().post("/agentican/tasks")
                .then()
                .statusCode(201)
                .body("taskId", notNullValue())
                .extract().jsonPath().getString("taskId");

        await().atMost(10, TimeUnit.SECONDS).pollInterval(200, TimeUnit.MILLISECONDS).untilAsserted(() ->
                given()
                        .when().get("/agentican/tasks/" + taskId)
                        .then()
                        .statusCode(200)
                        .body("status", equalTo("COMPLETED")));

        given()
                .when().get("/agentican/tasks/" + taskId + "/log")
                .then()
                .statusCode(200)
                .body("taskId", equalTo(taskId))
                .body("status", equalTo("COMPLETED"))
                .body("steps[0].stepName", equalTo("research"))
                .body("steps[0].status", equalTo("COMPLETED"))
                .body("steps[0].runCount", equalTo(1));
    }

    @Test
    void taskAppearsInListEndpointAfterCompletion() throws Exception {

        mockLlm.queueEndTurn("Result from list test.");

        var step = PlanStepAgent.of("work", "researcher", "Do something",
                List.of(), false, List.of(), List.of());
        var task = Plan.of("e2e-list", "List test", List.of(), List.of(step));
        var taskJson = objectMapper.writeValueAsString(task);

        var taskId = given()
                .contentType("application/json")
                .body("{\"task\": " + taskJson + "}")
                .when().post("/agentican/tasks")
                .then()
                .statusCode(201)
                .extract().jsonPath().getString("taskId");

        await().atMost(10, TimeUnit.SECONDS).pollInterval(200, TimeUnit.MILLISECONDS).untilAsserted(() ->
                given()
                        .when().get("/agentican/tasks/" + taskId)
                        .then()
                        .body("status", equalTo("COMPLETED")));

        given()
                .when().get("/agentican/tasks?status=COMPLETED")
                .then()
                .statusCode(200)
                .body("taskId", org.hamcrest.Matchers.hasItem(taskId));
    }
}
