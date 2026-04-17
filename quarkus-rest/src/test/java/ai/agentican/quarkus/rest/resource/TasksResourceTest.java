package ai.agentican.quarkus.rest.resource;

import ai.agentican.framework.state.TaskStateStore;
import ai.agentican.framework.orchestration.model.Plan;
import ai.agentican.framework.orchestration.execution.TaskStatus;
import ai.agentican.framework.util.Ids;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;

@QuarkusTest
class TasksResourceTest {

    @Inject
    TaskStateStore taskStateStore;

    @Test
    void listReturnsTasksFromLogStore() {

        seedTask("rest-list-1", TaskStatus.COMPLETED);

        given()
                .when().get("/agentican/tasks")
                .then()
                .statusCode(200)
                .body(containsString("rest-list-1"));
    }

    @Test
    void getReturnsTaskSummary() {

        seedTask("rest-get-1", TaskStatus.COMPLETED);

        given()
                .when().get("/agentican/tasks/rest-get-1")
                .then()
                .statusCode(200)
                .body("taskId", equalTo("rest-get-1"))
                .body("status", equalTo("COMPLETED"));
    }

    @Test
    void getReturns404ForUnknownTask() {

        given()
                .when().get("/agentican/tasks/does-not-exist")
                .then()
                .statusCode(404);
    }

    @Test
    void getLogReturnsFullTaskLog() {

        seedTask("rest-log-1", TaskStatus.COMPLETED);

        given()
                .when().get("/agentican/tasks/rest-log-1/log")
                .then()
                .statusCode(200)
                .body("taskId", equalTo("rest-log-1"));
    }

    @Test
    void cancelReturns404ForUnknownTask() {

        given()
                .when().delete("/agentican/tasks/does-not-exist")
                .then()
                .statusCode(404);
    }

    @Test
    void submitWithEmptyDescriptionReturns400() {

        given()
                .contentType("application/json")
                .body("{\"description\": \"\"}")
                .when().post("/agentican/tasks")
                .then()
                .statusCode(400)
                .body("code", equalTo("bad_request"));
    }

    @Test
    void submitWithBothDescriptionAndTaskReturns400() {

        given()
                .contentType("application/json")
                .body("{\"description\": \"hi\", \"task\": {\"name\": \"x\", \"steps\": [" +
                      "{\"type\": \"agent\", \"name\": \"s\", \"agentId\": \"researcher\", \"instructions\": \"i\"}]}}")
                .when().post("/agentican/tasks")
                .then()
                .statusCode(400)
                .body("code", equalTo("bad_request"));
    }

    @Test
    void getReturnsStructuredErrorBodyOn404() {

        given()
                .when().get("/agentican/tasks/does-not-exist")
                .then()
                .statusCode(404)
                .body("code", equalTo("not_found"))
                .body("message", containsString("does-not-exist"));
    }

    @Test
    void listLimitClampsToMax() {

        for (var i = 0; i < 5; i++) {
            seedTask("rest-paginate-" + i, TaskStatus.COMPLETED);
        }

        given()
                .when().get("/agentican/tasks?limit=2")
                .then()
                .statusCode(200)
                .body("$", hasSize(lessThanOrEqualTo(2)));
    }

    @Test
    void getLogReturnsStepRunsList() {

        var task = Plan.builder("demo").description("d").step("s", "a", "i").build();
        taskStateStore.taskStarted("rest-runs-1", "demo", task, Map.of());
        var stepId = Ids.generate();
        taskStateStore.stepStarted("rest-runs-1", stepId, "s");
        taskStateStore.stepCompleted("rest-runs-1", stepId, TaskStatus.COMPLETED, "done");
        taskStateStore.taskCompleted("rest-runs-1", TaskStatus.COMPLETED);

        given()
                .when().get("/agentican/tasks/rest-runs-1/log")
                .then()
                .statusCode(200)
                .body("steps[0].runs", org.hamcrest.Matchers.notNullValue());
    }

    private void seedTask(String taskId, TaskStatus status) {

        var task = Plan.builder("demo").description("d").step("s", "a", "i").build();
        taskStateStore.taskStarted(taskId, "demo", task, Map.of());
        taskStateStore.taskCompleted(taskId, status);
    }
}
