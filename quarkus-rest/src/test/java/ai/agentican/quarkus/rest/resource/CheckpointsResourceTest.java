package ai.agentican.quarkus.rest.resource;

import ai.agentican.framework.hitl.HitlManager;
import ai.agentican.framework.hitl.HitlResponse;
import ai.agentican.framework.llm.ToolCall;
import ai.agentican.quarkus.event.HitlCheckpointEvent;
import ai.agentican.quarkus.rest.TaskEventBus;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@QuarkusTest
class CheckpointsResourceTest {

    @Inject
    HitlManager hitlManager;

    @Inject
    TaskEventBus eventBus;

    @Test
    void listAllReturnsEmptyMapInitially() {

        given()
                .when().get("/agentican/checkpoints")
                .then()
                .statusCode(200);
    }

    @Test
    void respondWithUnknownCheckpointReturns404() {

        given()
                .contentType("application/json")
                .body("{\"approved\": true}")
                .when().post("/agentican/checkpoints/does-not-exist/respond")
                .then()
                .statusCode(404)
                .body("code", equalTo("not_found"));
    }

    @Test
    void cancelEndpointResolvesCheckpointAsRejected() throws Exception {

        var toolCall = new ToolCall("call-cancel", "send_email", Map.of("to", "user@example.com"));
        var checkpoint = hitlManager.createToolApprovalCheckpoint(toolCall, "step-cancel");

        eventBus.onHitlCheckpoint(new HitlCheckpointEvent("test-task-cancel", "step-id-cancel", "step-cancel", checkpoint));

        var responseFuture = CompletableFuture.supplyAsync(
                () -> hitlManager.awaitResponse(checkpoint.id()),
                java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor());

        given()
                .when().post("/agentican/checkpoints/" + checkpoint.id() + "/cancel")
                .then()
                .statusCode(204);

        var response = responseFuture.get(5, TimeUnit.SECONDS);

        assertEquals(false, response.approved());
        assertEquals(0, eventBus.pendingFor("test-task-cancel").size());
    }

    @Test
    void cancelWithUnknownCheckpointReturns404() {

        given()
                .when().post("/agentican/checkpoints/does-not-exist/cancel")
                .then()
                .statusCode(404)
                .body("code", equalTo("not_found"));
    }

    @Test
    void restRespondWakesParkedVirtualThread() throws Exception {

        var toolCall = new ToolCall("call-1", "send_email", Map.of("to", "user@example.com"));
        var checkpoint = hitlManager.createToolApprovalCheckpoint(toolCall, "step-1");

        eventBus.onHitlCheckpoint(new HitlCheckpointEvent("test-task", "step-id-1", "step-1", checkpoint));

        var responseFuture = CompletableFuture.supplyAsync(
                () -> hitlManager.awaitResponse(checkpoint.id()),
                java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor());

        given()
                .when().get("/agentican/checkpoints/test-task")
                .then()
                .statusCode(200)
                .body("id", hasItem(checkpoint.id()));

        given()
                .contentType("application/json")
                .body("{\"approved\": true, \"feedback\": \"approved by REST\"}")
                .when().post("/agentican/checkpoints/" + checkpoint.id() + "/respond")
                .then()
                .statusCode(204);

        var response = responseFuture.get(5, TimeUnit.SECONDS);

        assertNotNull(response, "Parked thread should have received the HitlResponse");
        assertEquals(true, response.approved());
        assertEquals("approved by REST", response.feedback());

        assertEquals(0, eventBus.pendingFor("test-task").size());
    }
}
