package ai.agentican.quarkus.rest.resource;

import ai.agentican.framework.orchestration.model.Plan;
import ai.agentican.framework.orchestration.model.PlanStep;
import ai.agentican.framework.orchestration.model.PlanStepAgent;
import ai.agentican.framework.orchestration.model.PlanStepBranch;
import ai.agentican.framework.orchestration.model.PlanStepLoop;
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

        var step = PlanStepAgent.of("s1", "researcher", "do research", List.of(), false, List.of(), List.of());
        var json = objectMapper.writeValueAsString(step);
        var deserialized = objectMapper.readValue(json, PlanStep.class);

        assertInstanceOf(PlanStepAgent.class, deserialized);

        var agent = (PlanStepAgent) deserialized;
        assertEquals("s1", agent.name());
        assertEquals("researcher", agent.agentId());
    }

    @Test
    void loopStepRoundTrips() throws Exception {

        var body = PlanStepAgent.of("body", "worker", "process item", List.of(), false, List.of(), List.of());
        var loop = new PlanStepLoop("loop1", "producer", List.of(body), List.of(), false);

        var json = objectMapper.writeValueAsString(loop);
        var deserialized = objectMapper.readValue(json, PlanStep.class);

        assertInstanceOf(PlanStepLoop.class, deserialized);

        var loopStep = (PlanStepLoop) deserialized;
        assertEquals("loop1", loopStep.name());
        assertEquals("producer", loopStep.over());
        assertEquals(1, loopStep.body().size());
        assertInstanceOf(PlanStepAgent.class, loopStep.body().getFirst());
    }

    @Test
    void branchStepRoundTrips() throws Exception {

        var pathA = PlanStepBranch.Path.of("yes", List.of(
                PlanStepAgent.of("a1", "researcher", "investigate", List.of(), false, List.of(), List.of())));
        var pathB = PlanStepBranch.Path.of("no", List.of(
                PlanStepAgent.of("b1", "writer", "skip", List.of(), false, List.of(), List.of())));

        var branch = PlanStepBranch.of("branch1", "classifier", List.of(pathA, pathB), "no", List.of(), false);

        var json = objectMapper.writeValueAsString(branch);
        var deserialized = objectMapper.readValue(json, PlanStep.class);

        assertInstanceOf(PlanStepBranch.class, deserialized);

        var branchStep = (PlanStepBranch) deserialized;
        assertEquals("branch1", branchStep.name());
        assertEquals("classifier", branchStep.from());
        assertEquals(2, branchStep.paths().size());
    }

    @Test
    void fullTaskWithMixedStepsRoundTrips() throws Exception {

        var producer = PlanStepAgent.of("produce", "researcher", "find items", List.of(), false, List.of(), List.of());
        var loopBody = PlanStepAgent.of("process", "worker", "handle item", List.of(), false, List.of(), List.of());
        var loop = new PlanStepLoop("loop", "produce", List.of(loopBody), List.of(), false);

        var task = Plan.of("mixed-task", "test", List.of(), List.of(producer, loop));

        var json = objectMapper.writeValueAsString(task);
        var deserialized = objectMapper.readValue(json, Plan.class);

        assertEquals("mixed-task", deserialized.name());
        assertEquals(2, deserialized.steps().size());
        assertInstanceOf(PlanStepAgent.class, deserialized.steps().get(0));
        assertInstanceOf(PlanStepLoop.class, deserialized.steps().get(1));
    }

    @Test
    void submitTaskWithLoopStepViaRest() throws Exception {

        var producer = PlanStepAgent.of("produce", "researcher", "find items", List.of(), false, List.of(), List.of());
        var loopBody = PlanStepAgent.of("process", "researcher", "handle item", List.of(), false, List.of(), List.of());
        var loop = new PlanStepLoop("loop", "produce", List.of(loopBody), List.of(), false);

        var task = Plan.of("rest-loop-task", "test with loop", List.of(), List.of(producer, loop));

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
