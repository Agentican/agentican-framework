package ai.agentican.framework.orchestration.execution.resume;

import ai.agentican.framework.llm.LlmRequest;
import ai.agentican.framework.llm.LlmResponse;
import ai.agentican.framework.llm.StopReason;
import ai.agentican.framework.llm.ToolCall;
import ai.agentican.framework.orchestration.execution.TaskStatus;
import ai.agentican.framework.orchestration.model.Plan;
import ai.agentican.framework.orchestration.model.PlanStepAgent;
import ai.agentican.framework.state.RunLog;
import ai.agentican.framework.state.StepLog;
import ai.agentican.framework.state.TaskLog;
import ai.agentican.framework.state.TurnLog;
import ai.agentican.framework.tools.ToolResult;
import ai.agentican.framework.util.Ids;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ResumeClassifierTest {

    private static final LlmRequest REQUEST =
            new LlmRequest("sys", null, "user", List.of(), 0, "default", "anthropic", "claude");

    @Test
    void nullTaskLogReaps() {

        var result = ResumeClassifier.classify(null, fakePlan());
        assertTrue(result.reapOnly());
        assertEquals(ai.agentican.framework.orchestration.execution.resume.ReapReason.TASK_LOG_MISSING, result.reapReason());
    }

    @Test
    void missingPlanReaps() {

        var taskLog = new TaskLog("t-1", "t", null, Map.of());
        var result = ResumeClassifier.classify(taskLog, null);
        assertTrue(result.reapOnly());
        assertEquals(ai.agentican.framework.orchestration.execution.resume.ReapReason.PLAN_UNAVAILABLE, result.reapReason());
    }

    @Test
    void terminalTaskReaps() {

        var taskLog = new TaskLog("t-1", "t", null, Map.of());
        taskLog.setStatus(TaskStatus.COMPLETED);

        var result = ResumeClassifier.classify(taskLog, fakePlan());
        assertTrue(result.reapOnly());
    }

    @Test
    void taskWithNoStepsClassifiesCleanly() {

        var taskLog = new TaskLog("t-1", "t", null, Map.of());
        var result = ResumeClassifier.classify(taskLog, fakePlan());

        assertFalse(result.reapOnly());
        assertTrue(result.completedSteps().isEmpty());
        assertTrue(result.inFlightStep().isEmpty());
        assertEquals(TurnResumeState.NONE, result.turnState());
    }

    @Test
    void stepStartedNoRunsClassifiedAsInFlightWithNoTurn() {

        var plan = fakePlan();
        var taskLog = new TaskLog("t-1", "t", null, Map.of());
        taskLog.addStep("research", new StepLog(Ids.generate(), "research"));

        var result = ResumeClassifier.classify(taskLog, plan);
        assertTrue(result.inFlightStep().isPresent());
        assertTrue(result.inFlightRun().isEmpty());
        assertEquals(TurnResumeState.NONE, result.turnState());
    }

    @Test
    void turnStartedNoMessage() {

        var taskLog = taskWithInFlightTurn(turn -> { });

        var result = ResumeClassifier.classify(taskLog, fakePlan());
        assertEquals(TurnResumeState.STARTED_NO_MESSAGE, result.turnState());
    }

    @Test
    void messageSentNoResponse() {

        var taskLog = taskWithInFlightTurn(turn -> turn.setRequest(REQUEST));

        var result = ResumeClassifier.classify(taskLog, fakePlan());
        assertEquals(TurnResumeState.MESSAGE_SENT, result.turnState());
    }

    @Test
    void responseReceivedNoTools() {

        var response = new LlmResponse("done", List.of(), StopReason.END_TURN, 1, 1, 0, 0, 0);
        var taskLog = taskWithInFlightTurn(turn -> { turn.setRequest(REQUEST); turn.setResponse(response); });

        var result = ResumeClassifier.classify(taskLog, fakePlan());
        assertEquals(TurnResumeState.TOOLS_COMPLETE, result.turnState());
        assertTrue(result.toolsToExecute().isEmpty());
    }

    @Test
    void responseReceivedNoToolCallsYetExecuted() {

        var toolCalls = List.of(new ToolCall("tc-1", "FOO", Map.of()));
        var response = new LlmResponse("go", toolCalls, StopReason.TOOL_USE, 1, 1, 0, 0, 0);
        var taskLog = taskWithInFlightTurn(turn -> { turn.setRequest(REQUEST); turn.setResponse(response); });

        var result = ResumeClassifier.classify(taskLog, fakePlan());
        assertEquals(TurnResumeState.RESPONSE_RECEIVED, result.turnState());
        assertEquals(1, result.toolsToExecute().size());
        assertEquals("tc-1", result.toolsToExecute().getFirst().id());
    }

    @Test
    void toolsPartial() {

        var calls = List.of(
                new ToolCall("tc-1", "FOO", Map.of()),
                new ToolCall("tc-2", "BAR", Map.of()));
        var response = new LlmResponse("go", calls, StopReason.TOOL_USE, 1, 1, 0, 0, 0);

        var taskLog = taskWithInFlightTurn(turn -> {
            turn.setRequest(REQUEST);
            turn.setResponse(response);
            turn.addToolResult(new ToolResult("tc-1", "FOO", "{}"));
            turn.addToolResult(ToolResult.started("tc-2", "BAR"));
        });

        var result = ResumeClassifier.classify(taskLog, fakePlan());
        assertEquals(TurnResumeState.TOOLS_PARTIAL, result.turnState());
        assertEquals(1, result.toolsToExecute().size());
        assertEquals("tc-2", result.toolsToExecute().getFirst().id());
    }

    @Test
    void toolsCompleteTurnStillOpen() {

        var calls = List.of(new ToolCall("tc-1", "FOO", Map.of()));
        var response = new LlmResponse("go", calls, StopReason.TOOL_USE, 1, 1, 0, 0, 0);

        var taskLog = taskWithInFlightTurn(turn -> {
            turn.setRequest(REQUEST);
            turn.setResponse(response);
            turn.addToolResult(new ToolResult("tc-1", "FOO", "{}"));
        });

        var result = ResumeClassifier.classify(taskLog, fakePlan());
        assertEquals(TurnResumeState.TOOLS_COMPLETE, result.turnState());
        assertTrue(result.toolsToExecute().isEmpty());
    }

    @Test
    void turnClosedDetectedViaCompletedAtOnly() {

        var taskLog = new TaskLog("t-1", "t", null, Map.of());
        var step = new StepLog(Ids.generate(), "research");
        taskLog.addStep("research", step);
        var run = new RunLog(Ids.generate(), 0, "Researcher");
        step.addRun(run);
        var turn = new TurnLog(Ids.generate(), 0, null, REQUEST, null,
                new LlmResponse("done", List.of(), StopReason.END_TURN, 1, 1, 0, 0, 0),
                List.of(), Instant.now().minusSeconds(5), Instant.now(), TurnLog.State.COMPLETED);
        run.addTurn(turn);

        var result = ResumeClassifier.classify(taskLog, fakePlan());
        assertEquals(TurnResumeState.CLOSED, result.turnState());
    }

    @Test
    void completedStepsAreCollectedInOrder() {

        var plan = Plan.builder("t").description("").steps(List.of(
                new PlanStepAgent("a", "x", "do a", List.of(), false, List.of(), List.of()),
                new PlanStepAgent("b", "x", "do b", List.of("a"), false, List.of(), List.of()),
                new PlanStepAgent("c", "x", "do c", List.of("b"), false, List.of(), List.of())))
                .build();

        var taskLog = new TaskLog("t-1", "t", null, Map.of());

        var aStep = new StepLog(Ids.generate(), "a"); aStep.setStatus(TaskStatus.COMPLETED);
        var bStep = new StepLog(Ids.generate(), "b"); bStep.setStatus(TaskStatus.COMPLETED);
        var cStep = new StepLog(Ids.generate(), "c");

        taskLog.addStep("a", aStep);
        taskLog.addStep("b", bStep);
        taskLog.addStep("c", cStep);

        var result = ResumeClassifier.classify(taskLog, plan);
        assertEquals(2, result.completedSteps().size());
        assertEquals("a", result.completedSteps().get(0).stepName());
        assertEquals("b", result.completedSteps().get(1).stepName());
        assertTrue(result.inFlightStep().isPresent());
        assertEquals("c", result.inFlightStep().get().stepName());
    }

    private static Plan fakePlan() {

        return Plan.builder("t").description("")
                .step(new PlanStepAgent("research", "researcher", "do it",
                        List.of(), false, List.of(), List.of()))
                .build();
    }

    private static TaskLog taskWithInFlightTurn(java.util.function.Consumer<TurnLog> setup) {

        var taskLog = new TaskLog("t-1", "t", null, Map.of());
        var step = new StepLog(Ids.generate(), "research");
        taskLog.addStep("research", step);
        var run = new RunLog(Ids.generate(), 0, "Researcher");
        step.addRun(run);
        var turn = new TurnLog(Ids.generate(), 0);
        setup.accept(turn);
        run.addTurn(turn);
        return taskLog;
    }
}
