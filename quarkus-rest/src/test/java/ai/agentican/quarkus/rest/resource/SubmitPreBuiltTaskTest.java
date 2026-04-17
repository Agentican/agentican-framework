package ai.agentican.quarkus.rest.resource;

import ai.agentican.framework.orchestration.model.Plan;
import ai.agentican.framework.orchestration.model.PlanStepAgent;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.notNullValue;

@QuarkusTest
class SubmitPreBuiltTaskTest {

    @Inject
    ObjectMapper objectMapper;

    @Test
    void submitWithPreBuiltTaskReturnsTaskId() throws Exception {

        var step = PlanStepAgent.of("research", "researcher", "do something",
                List.of(), false, List.of(), List.of());

        var task = Plan.of("rest-prebuilt-task", "test description", List.of(), List.of(step));

        var taskJson = objectMapper.writeValueAsString(task);

        var body = "{\"task\": " + taskJson + "}";

        given()
                .contentType("application/json")
                .body(body)
                .when().post("/agentican/tasks")
                .then()
                .statusCode(201)
                .body("taskId", notNullValue());
    }

    @Test
    void submitWithNeitherDescriptionNorTaskReturns400() {

        given()
                .contentType("application/json")
                .body("{}")
                .when().post("/agentican/tasks")
                .then()
                .statusCode(400);
    }
}
