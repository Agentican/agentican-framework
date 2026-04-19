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

        var step = new PlanStepAgent("research", "researcher", "do something",
                List.of(), false, List.of(), List.of());

        var task = Plan.builder("rest-prebuilt-task").description("test description").step(step).build();

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
