package ai.agentican.quarkus.rest.resource;

import ai.agentican.framework.store.WorkflowRunStore;
import ai.agentican.framework.orchestration.model.WorkflowDefinition;
import ai.agentican.framework.orchestration.execution.WorkflowRunStatus;
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
    WorkflowRunStore workflowRunStore;

    @Test
    void listReturnsTasksFromLogStore() {

        seedTask("rest-list-1", WorkflowRunStatus.COMPLETED);

        given()
                .when().get("/agentican/tasks")
                .then()
                .statusCode(200)
                .body(containsString("rest-list-1"));
    }

    @Test
    void getReturnsTaskSummary() {

        seedTask("rest-get-1", WorkflowRunStatus.COMPLETED);

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

        seedTask("rest-log-1", WorkflowRunStatus.COMPLETED);

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
                      "{\"type\": \"agent\", \"name\": \"s\", \"agentName\": \"researcher\", \"instructions\": \"i\"}]}}")
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
            seedTask("rest-paginate-" + i, WorkflowRunStatus.COMPLETED);
        }

        given()
                .when().get("/agentican/tasks?limit=2")
                .then()
                .statusCode(200)
                .body("$", hasSize(lessThanOrEqualTo(2)));
    }

    @Test
    void getLogReturnsStepRunsList() {

        var task = WorkflowDefinition.builder("demo", "demo").description("d")
                .step().name("s").agent("a").instructions("i").end()
                .build();
        workflowRunStore.taskStarted("rest-runs-1", "demo", task, Map.of());
        var stepId = Ids.generate();
        workflowRunStore.stepStarted("rest-runs-1", stepId, "s");
        workflowRunStore.stepCompleted("rest-runs-1", stepId, WorkflowRunStatus.COMPLETED, "done");
        workflowRunStore.taskCompleted("rest-runs-1", WorkflowRunStatus.COMPLETED);

        given()
                .when().get("/agentican/tasks/rest-runs-1/log")
                .then()
                .statusCode(200)
                .body("steps[0].runs", org.hamcrest.Matchers.notNullValue());
    }

    private void seedTask(String taskId, WorkflowRunStatus status) {

        var task = WorkflowDefinition.builder("demo", "demo").description("d")
                .step().name("s").agent("a").instructions("i").end()
                .build();
        workflowRunStore.taskStarted(taskId, "demo", task, Map.of());
        workflowRunStore.taskCompleted(taskId, status);
    }
}
