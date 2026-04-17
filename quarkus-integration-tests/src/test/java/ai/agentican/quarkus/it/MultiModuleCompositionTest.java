package ai.agentican.quarkus.it;

import ai.agentican.framework.Agentican;
import ai.agentican.framework.TaskListener;
import ai.agentican.framework.TaskDecorator;
import ai.agentican.framework.llm.LlmClientDecorator;
import ai.agentican.quarkus.test.MockLlmClient;
import ai.agentican.quarkus.test.TestTaskBuilder;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static io.restassured.RestAssured.given;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class MultiModuleCompositionTest {

    @Inject
    Agentican agentican;

    @Inject
    MockLlmClient mockLlm;

    @Inject
    Instance<LlmClientDecorator> llmDecorators;

    @Inject
    Instance<TaskDecorator> taskDecorators;

    @Inject
    Instance<TaskListener> stepListeners;

    @BeforeEach
    void reset() {

        mockLlm.reset();
    }

    @Test
    void multipleDecoratorBeansAreAvailable() {

        var decoratorCount = llmDecorators.stream().count();

        assertTrue(decoratorCount >= 2,
                "Should have at least 2 LlmClientDecorators (metrics + otel). Got: " + decoratorCount);
    }

    @Test
    void multipleTaskDecoratorsAreAvailable() {

        var count = taskDecorators.stream().count();

        assertTrue(count >= 1,
                "Should have at least 1 TaskDecorator (otel). Got: " + count);
    }

    @Test
    void multipleTaskListenersAreAvailable() {

        var count = stepListeners.stream().count();

        assertTrue(count >= 2,
                "Should have at least 2 TaskListeners (otel lifecycle + metrics). Got: " + count);
    }

    @Test
    void taskCompletesWithAllModulesActive() {

        mockLlm.queueEndTurn("Composition test result");

        var task = TestTaskBuilder.singleStep("composition-test", "researcher", "do work");

        var handle = agentican.run(task);
        var result = handle.result();

        assertEquals("COMPLETED", result.status().name(),
                "Task should complete with all modules. Got: " + result.status());
    }

    @Test
    void restEndpointsWorkAlongsideMetricsAndTracing() {

        given().when().get("/agentican/agents")
                .then().statusCode(200).body("[0].name", equalTo("researcher"));
    }

    @Test
    void metricsEndpointShowsLifecycleMetrics() {

        mockLlm.queueEndTurn("Metrics test");

        agentican.run(TestTaskBuilder.singleStep("metrics-composition", "researcher", "do work")).result();

        given().when().get("/q/metrics")
                .then().statusCode(200).body(containsString("agentican_tasks_completed_total"));
    }

    @Test
    void submitViaRestAndVerifyCompletion() {

        mockLlm.queueEndTurn("REST composition result");

        var taskId = given()
                .contentType("application/json")
                .body("{\"task\": {\"name\": \"rest-compose\", \"description\": \"d\", " +
                      "\"steps\": [{\"type\": \"agent\", \"name\": \"s\", " +
                      "\"agentId\": \"researcher\", \"instructions\": \"do it\"}]}}")
                .when().post("/agentican/tasks")
                .then().statusCode(201).body("taskId", notNullValue())
                .extract().jsonPath().getString("taskId");

        await().atMost(10, TimeUnit.SECONDS).pollInterval(200, TimeUnit.MILLISECONDS).untilAsserted(() ->
                given().when().get("/agentican/tasks/" + taskId)
                        .then().body("status", equalTo("COMPLETED")));
    }
}
