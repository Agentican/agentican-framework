package ai.agentican.quarkus.store.jpa;

import ai.agentican.framework.llm.LlmRequest;
import ai.agentican.framework.llm.LlmResponse;
import ai.agentican.framework.llm.StopReason;
import ai.agentican.framework.llm.TokenUsage;
import ai.agentican.framework.orchestration.execution.TaskStatus;
import ai.agentican.framework.state.TaskStateStore;
import ai.agentican.framework.tools.ToolResult;
import ai.agentican.framework.util.Ids;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class JpaTaskStateStoreTest {

    @Inject
    JpaTaskStateStore store;

    @Inject
    TaskStateStore storeInterface;

    @Test
    void interfaceResolvesToJpaBean() {

        assertSame(store, storeInterface,
                "TaskStateStore interface should resolve to JpaTaskStateStore when backend property is missing");
    }

    @Test
    void taskLifecycleRoundTrips() {

        var taskId = "t-" + Ids.generate();
        store.taskStarted(taskId, "demo task", null, Map.of("k", "v"));
        store.taskCompleted(taskId, TaskStatus.COMPLETED);

        var fetched = store.load(taskId);
        assertNotNull(fetched);
        assertEquals("demo task", fetched.taskName());
        assertEquals(TaskStatus.COMPLETED, fetched.status());
        assertEquals("v", fetched.params().get("k"));
        assertNotNull(fetched.completedAt());
    }

    @Test
    void reconstructedTaskPreservesCreatedAt() throws InterruptedException {

        var taskId = "t-" + Ids.generate();
        store.taskStarted(taskId, "time check", null, Map.of());

        Thread.sleep(60);

        var before = java.time.Instant.now();
        var fetched = store.load(taskId);

        assertNotNull(fetched.createdAt(), "createdAt must be populated");
        assertTrue(fetched.createdAt().isBefore(before),
                "reconstructed createdAt must reflect the original insertion time, not now — "
                        + "got " + fetched.createdAt() + ", load() ran at " + before);
    }

    @Test
    void subTaskLineageIsPersisted() {

        var parent = "parent-" + Ids.generate();
        var parentStep = "ps-" + Ids.generate();
        store.taskStarted(parent, "parent", null, Map.of());
        store.stepStarted(parent, parentStep, "loop-step");

        var child = "child-" + Ids.generate();
        store.taskStarted(child, "iter-1", null, Map.of(), parent, parentStep, 2);

        var loaded = store.load(child);
        assertEquals(parent, loaded.parentTaskId());
        assertEquals(parentStep, loaded.parentStepId());
        assertEquals(2, loaded.iterationIndex());
    }

    @Test
    void stepWithRunAndTurnAndToolResultReconstitutes() {

        var taskId = "t-" + Ids.generate();
        var stepId = "s-" + Ids.generate();
        var runId = "r-" + Ids.generate();
        var turnId = "u-" + Ids.generate();

        store.taskStarted(taskId, "demo", null, Map.of());
        store.stepStarted(taskId, stepId, "research");
        store.runStarted(taskId, stepId, runId, "Researcher");
        store.turnStarted(taskId, runId, turnId);

        var req = new LlmRequest("system", null, "hello", List.of(), 0, null, null, null);
        store.messageSent(taskId, turnId, req);

        var resp = new LlmResponse("response text", List.of(), StopReason.END_TURN, 10, 20, 0, 0, 0);
        store.responseReceived(taskId, turnId, resp);

        store.toolCallCompleted(taskId, turnId,
                new ToolResult("tc-1", "my_tool", "{\"ok\":true}"));

        store.turnCompleted(taskId, turnId);
        store.runCompleted(taskId, runId);
        store.stepCompleted(taskId, stepId, TaskStatus.COMPLETED, "step output");
        store.taskCompleted(taskId, TaskStatus.COMPLETED);

        var loaded = store.load(taskId);
        assertNotNull(loaded);
        assertEquals(1, loaded.steps().size());

        var step = loaded.step("research");
        assertEquals(TaskStatus.COMPLETED, step.status());
        assertEquals("step output", step.output());
        assertEquals(1, step.runs().size());

        var run = step.runs().getFirst();
        assertEquals("Researcher", run.agentName());
        assertEquals(1, run.turns().size());

        var turn = run.turns().getFirst();
        assertNotNull(turn.request());
        assertEquals("hello", turn.request().userMessage());
        assertNotNull(turn.response());
        assertEquals("response text", turn.response().text());
        assertEquals(10, turn.response().inputTokens());
        assertEquals(1, turn.toolResults().size());
        assertEquals("my_tool", turn.toolResults().getFirst().toolName());
    }

    @Test
    void aggregateTokenUsagePersists() {

        var taskId = "t-" + Ids.generate();
        var stepId = "s-" + Ids.generate();
        store.taskStarted(taskId, "demo", null, Map.of());
        store.stepStarted(taskId, stepId, "loop-step");

        var usage = new TokenUsage(100, 200, 50, 75, 3);
        store.stepTokenUsageAggregated(taskId, stepId, usage);

        var loaded = store.load(taskId);
        var step = loaded.step("loop-step");
        assertEquals(100, step.inputTokens());
        assertEquals(200, step.outputTokens());
        assertEquals(50, step.cacheReadTokens());
        assertEquals(75, step.cacheWriteTokens());
        assertEquals(3, step.webSearchRequests());
    }

    @Test
    void toolCallStateTransitionsStartedThenCompleted() {

        var taskId = "t-" + Ids.generate();
        var stepId = "s-" + Ids.generate();
        var runId = "r-" + Ids.generate();
        var turnId = "u-" + Ids.generate();

        store.taskStarted(taskId, "tool state test", null, Map.of());
        store.stepStarted(taskId, stepId, "do-work");
        store.runStarted(taskId, stepId, runId, "Worker");
        store.turnStarted(taskId, runId, turnId);

        var tc = new ai.agentican.framework.llm.ToolCall("tc-in-flight", "MY_TOOL", Map.of());
        store.toolCallStarted(taskId, turnId, tc);

        long startedRows = ai.agentican.quarkus.store.jpa.entity.ToolResultEntity
                .count("turnId = ?1 AND toolCallId = ?2 AND state = ?3", turnId, "tc-in-flight", "STARTED");
        assertEquals(1, startedRows, "Started tool call must leave a STARTED row");

        store.toolCallCompleted(taskId, turnId, new ToolResult("tc-in-flight", "MY_TOOL", "{\"ok\":true}"));

        long completedRows = ai.agentican.quarkus.store.jpa.entity.ToolResultEntity
                .count("turnId = ?1 AND toolCallId = ?2 AND state = ?3", turnId, "tc-in-flight", "COMPLETED");
        assertEquals(1, completedRows, "Completed tool call must transition STARTED → COMPLETED (same row)");

        long totalRows = ai.agentican.quarkus.store.jpa.entity.ToolResultEntity
                .count("turnId = ?1 AND toolCallId = ?2", turnId, "tc-in-flight");
        assertEquals(1, totalRows, "State machine updates the same row; no duplicate inserts");
    }

    @Test
    void turnStateTransitionsStartedThenCompleted() {

        var taskId = "t-" + Ids.generate();
        var stepId = "s-" + Ids.generate();
        var runId = "r-" + Ids.generate();
        var turnId = "u-" + Ids.generate();

        store.taskStarted(taskId, "turn state test", null, Map.of());
        store.stepStarted(taskId, stepId, "do-work");
        store.runStarted(taskId, stepId, runId, "Worker");
        store.turnStarted(taskId, runId, turnId);

        long startedCount = ai.agentican.quarkus.store.jpa.entity.TurnEntity
                .count("id = ?1 AND state = ?2", turnId, "STARTED");
        assertEquals(1, startedCount, "Freshly-started turn must be in STARTED state");

        store.turnCompleted(taskId, turnId);

        long completedCount = ai.agentican.quarkus.store.jpa.entity.TurnEntity
                .count("id = ?1 AND state = ?2", turnId, "COMPLETED");
        assertEquals(1, completedCount, "Closed turn must transition to COMPLETED");
    }

    @Test
    void planSnapshotIsPersistedOnTaskStarted() {

        var step = ai.agentican.framework.orchestration.model.PlanStepAgent.of(
                "work", "worker-agent", "do it", List.of(), false, List.of(), List.of());
        var plan = ai.agentican.framework.orchestration.model.Plan.of(
                "Snapshot Plan", "test", List.of(), List.of(step));

        var taskId = "t-" + Ids.generate();
        store.taskStarted(taskId, "snap", plan, Map.of());

        var entity = (ai.agentican.quarkus.store.jpa.entity.TaskEntity)
                ai.agentican.quarkus.store.jpa.entity.TaskEntity.findById(taskId);
        assertNotNull(entity.planSnapshotJson, "plan_snapshot_json must be populated");
        assertTrue(entity.planSnapshotJson.contains("Snapshot Plan"),
                "Snapshot should include the plan's name");
        assertTrue(entity.planSnapshotJson.contains("worker-agent"),
                "Snapshot should include step's agentId");
    }

    @Test
    void branchPathChosenPersists() {

        var taskId = "t-" + Ids.generate();
        var stepId = "s-" + Ids.generate();
        store.taskStarted(taskId, "branch test", null, Map.of());
        store.stepStarted(taskId, stepId, "branch-step");

        store.branchPathChosen(taskId, stepId, "happy-path");

        long matches = ai.agentican.quarkus.store.jpa.entity.TaskStepEntity
                .count("id = ?1 AND branchChosenPath = ?2", stepId, "happy-path");
        assertEquals(1, matches, "branch_chosen_path must persist the selected path name");
    }

    @Test
    void listReturnsPersistedTasks() {

        var a = "list-a-" + Ids.generate();
        var b = "list-b-" + Ids.generate();
        store.taskStarted(a, "A", null, Map.of());
        store.taskStarted(b, "B", null, Map.of());

        var ids = store.list().stream().map(t -> t.taskId()).toList();
        assertTrue(ids.contains(a));
        assertTrue(ids.contains(b));
    }
}
