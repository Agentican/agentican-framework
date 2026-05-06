package ai.agentican.quarkus.rest.resource;

import ai.agentican.framework.orchestration.model.WorkflowDefinition;
import ai.agentican.framework.orchestration.model.WorkflowStep;
import ai.agentican.framework.orchestration.model.WorkflowStepAgent;
import ai.agentican.framework.orchestration.model.WorkflowStepBranch;
import ai.agentican.framework.orchestration.model.WorkflowStepLoop;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

@QuarkusTest
class PolymorphicPlanStepTest {

    @Inject
    ObjectMapper objectMapper;

    @Test
    void agentStepRoundTrips() throws Exception {

        var step = new WorkflowStepAgent("s1", "researcher", "do research", List.of(), false, List.of(), List.of());
        var json = objectMapper.writeValueAsString(step);
        var deserialized = objectMapper.readValue(json, WorkflowStep.class);

        assertInstanceOf(WorkflowStepAgent.class, deserialized);

        var agent = (WorkflowStepAgent) deserialized;
        assertEquals("s1", agent.name());
        assertEquals("researcher", agent.agentName());
    }

    @Test
    void loopStepRoundTrips() throws Exception {

        var body = new WorkflowStepAgent("body", "worker", "process item", List.of(), false, List.of(), List.of());
        var loop = new WorkflowStepLoop("loop1", "producer", List.of(body), List.of(), false);

        var json = objectMapper.writeValueAsString(loop);
        var deserialized = objectMapper.readValue(json, WorkflowStep.class);

        assertInstanceOf(WorkflowStepLoop.class, deserialized);

        var loopStep = (WorkflowStepLoop) deserialized;
        assertEquals("loop1", loopStep.name());
        assertEquals("producer", loopStep.over());
        assertEquals(1, loopStep.body().size());
        assertInstanceOf(WorkflowStepAgent.class, loopStep.body().getFirst());
    }

    @Test
    void branchStepRoundTrips() throws Exception {

        var pathA = new WorkflowStepBranch.Path("yes", List.of(
                new WorkflowStepAgent("a1", "researcher", "investigate", List.of(), false, List.of(), List.of())));
        var pathB = new WorkflowStepBranch.Path("no", List.of(
                new WorkflowStepAgent("b1", "writer", "skip", List.of(), false, List.of(), List.of())));

        var branch = new WorkflowStepBranch("branch1", "classifier", List.of(pathA, pathB), "no", List.of(), false);

        var json = objectMapper.writeValueAsString(branch);
        var deserialized = objectMapper.readValue(json, WorkflowStep.class);

        assertInstanceOf(WorkflowStepBranch.class, deserialized);

        var branchStep = (WorkflowStepBranch) deserialized;
        assertEquals("branch1", branchStep.name());
        assertEquals("classifier", branchStep.from());
        assertEquals(2, branchStep.paths().size());
    }

    @Test
    void fullTaskWithMixedStepsRoundTrips() throws Exception {

        var producer = new WorkflowStepAgent("produce", "researcher", "find items", List.of(), false, List.of(), List.of());
        var loopBody = new WorkflowStepAgent("process", "worker", "handle item", List.of(), false, List.of(), List.of());
        var loop = new WorkflowStepLoop("loop", "produce", List.of(loopBody), List.of(), false);

        var task = WorkflowDefinition.builder("mixed-task").description("test").steps(List.of(producer, loop)).build();

        var json = objectMapper.writeValueAsString(task);
        var deserialized = objectMapper.readValue(json, WorkflowDefinition.class);

        assertEquals("mixed-task", deserialized.name());
        assertEquals(2, deserialized.steps().size());
        assertInstanceOf(WorkflowStepAgent.class, deserialized.steps().get(0));
        assertInstanceOf(WorkflowStepLoop.class, deserialized.steps().get(1));
    }

    @Test
    void submitTaskWithLoopStepViaRest() throws Exception {

        var producer = new WorkflowStepAgent("produce", "researcher", "find items", List.of(), false, List.of(), List.of());
        var loopBody = new WorkflowStepAgent("process", "researcher", "handle item", List.of(), false, List.of(), List.of());
        var loop = new WorkflowStepLoop("loop", "produce", List.of(loopBody), List.of(), false);

        var task = WorkflowDefinition.builder("rest-loop-task").description("test with loop").steps(List.of(producer, loop)).build();

        var taskJson = objectMapper.writeValueAsString(task);

        given()
                .contentType("application/json")
                .body("{\"task\": " + taskJson + "}")
                .when().post("/agentican/tasks")
                .then()
                .statusCode(201)
                .body("taskId", notNullValue());
    }
}
