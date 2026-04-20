package ai.agentican.framework.agent;

import ai.agentican.framework.MockLlmClient;
import ai.agentican.framework.MockToolkit;
import ai.agentican.framework.llm.StopReason;
import ai.agentican.framework.llm.ToolCall;
import ai.agentican.framework.orchestration.execution.resume.ResumeClassifier;
import ai.agentican.framework.orchestration.execution.resume.ResumePlan;
import ai.agentican.framework.orchestration.execution.resume.TurnResumeState;
import ai.agentican.framework.store.TaskStateStoreMemory;
import ai.agentican.framework.tools.ToolDefinition;
import ai.agentican.framework.tools.ToolResult;
import ai.agentican.framework.tools.Toolkit;
import ai.agentican.framework.util.Ids;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static ai.agentican.framework.MockLlmClient.*;
import static org.junit.jupiter.api.Assertions.*;

import ai.agentican.framework.config.AgentConfig;
class SmacAgentRunnerResumeTest {

    private static Map<String, Toolkit> toolkitMap(MockToolkit t) {
        var m = new LinkedHashMap<String, Toolkit>();
        for (var def : t.toolDefinitions()) m.put(def.name(), t);
        return m;
    }

    private static Agent agent(SmacAgentRunner r) { return Agent.builder().config(AgentConfig.builder().name("resume-agent").role("Test role").build()).runner(r).build(); }

    @Test
    void resumeWithNoSavedTurnsBehavesAsFreshRun() {

        var store = new TaskStateStoreMemory();
        var mockLlm = new MockLlmClient().onSend("", endTurn("Fresh completion"));

        var runner = SmacAgentRunner.builder()
                .llmClient(mockLlm.toLlmClient())
                .taskStateStore(store)
                .maxIterations(3)
                .build();

        var taskId = "t-" + Ids.generate();
        var stepId = "s-" + Ids.generate();

        store.taskStarted(taskId, "resume", null, Map.of());
        store.stepStarted(taskId, stepId, "work");

        var emptyRun = new ai.agentican.framework.state.RunLog(Ids.generate(), 0, "resume-agent");

        var result = runner.resumeAfterCrash(agent(runner), "do it", taskId, stepId, "work", List.of(), Map.of(), null, emptyRun, new AtomicBoolean(false), new ResumePlan(List.of(), java.util.Optional.empty(),
                        java.util.Optional.of(emptyRun), java.util.Optional.empty(),
                        TurnResumeState.NONE, List.of(), false, null));

        assertEquals(AgentStatus.COMPLETED, result.status());
    }

    @Test
    void resumeStartedNoMessageAbandonsTurnAndRunsFresh() {

        var store = new TaskStateStoreMemory();
        var mockLlm = new MockLlmClient().onSend("", endTurn("Completed after resume"));

        var runner = SmacAgentRunner.builder()
                .llmClient(mockLlm.toLlmClient())
                .taskStateStore(store)
                .maxIterations(3)
                .build();

        var taskId = "t-" + Ids.generate();
        var stepId = "s-" + Ids.generate();
        var runId = Ids.generate();
        var deadTurnId = Ids.generate();

        store.taskStarted(taskId, "resume", null, Map.of());
        store.stepStarted(taskId, stepId, "work");
        store.runStarted(taskId, stepId, runId, "resume-agent");
        store.turnStarted(taskId, runId, deadTurnId);

        var taskLog = store.load(taskId);
        var run = taskLog.findStepById(stepId).lastRun();

        var resumePlan = ResumeClassifier.classify(taskLog,
                ai.agentican.framework.orchestration.model.Plan.builder("p").description("")
                        .step(new ai.agentican.framework.orchestration.model.PlanStepAgent(
                                "work", "resume-agent", "do it", List.of(), false, List.of(), List.of()))
                        .build());

        assertEquals(TurnResumeState.STARTED_NO_MESSAGE, resumePlan.turnState());

        var result = runner.resumeAfterCrash(agent(runner), "do it", taskId, stepId, "work", List.of(), Map.of(), null, run, new AtomicBoolean(false), resumePlan);

        assertEquals(AgentStatus.COMPLETED, result.status());

        var finalLog = store.load(taskId);
        var deadTurn = finalLog.findTurnById(deadTurnId);
        assertNotNull(deadTurn, "Abandoned turn should still exist in the log");
        assertEquals(ai.agentican.framework.state.TurnLog.State.ABANDONED, deadTurn.state(),
                "Turn that had no request must be marked ABANDONED");
    }

    @Test
    void resumeToolsPartialReExecutesOnlyMissingTools() {

        var store = new TaskStateStoreMemory();

        var toolkit = new MockToolkit(List.of(
                new ToolDefinition("FOO", "foo tool", Map.of("x", Map.of("type", "string")))
        )).onExecute("FOO", "{\"ok\":true}");

        var mockLlm = new MockLlmClient().onSend("", endTurn("Done after resume"));

        var runner = SmacAgentRunner.builder()
                .llmClient(mockLlm.toLlmClient())
                .taskStateStore(store)
                .maxIterations(3)
                .build();

        var taskId = "t-" + Ids.generate();
        var stepId = "s-" + Ids.generate();
        var runId = Ids.generate();
        var turnId = Ids.generate();

        store.taskStarted(taskId, "resume", null, Map.of());
        store.stepStarted(taskId, stepId, "work");
        store.runStarted(taskId, stepId, runId, "resume-agent");
        store.turnStarted(taskId, runId, turnId);

        store.messageSent(taskId, turnId,
                new ai.agentican.framework.llm.LlmRequest("sys", null, "u", List.of(), 0, "d", "a", "c"));

        var tc1 = new ToolCall("tc-1", "FOO", Map.of("x", "a"));
        var tc2 = new ToolCall("tc-2", "FOO", Map.of("x", "b"));
        var response = new ai.agentican.framework.llm.LlmResponse(
                "run tools", List.of(tc1, tc2), StopReason.TOOL_USE, 1, 1, 0, 0, 0);
        store.responseReceived(taskId, turnId, response);

        store.toolCallStarted(taskId, turnId, tc1);
        store.toolCallCompleted(taskId, turnId, new ToolResult("tc-1", "FOO", "{\"ok\":true}"));
        store.toolCallStarted(taskId, turnId, tc2);

        var taskLog = store.load(taskId);
        var run = taskLog.findStepById(stepId).lastRun();

        var resumePlan = ResumeClassifier.classify(taskLog,
                ai.agentican.framework.orchestration.model.Plan.builder("p").description("")
                        .step(new ai.agentican.framework.orchestration.model.PlanStepAgent(
                                "work", "resume-agent", "do it", List.of(), false, List.of(), List.of()))
                        .build());

        assertEquals(TurnResumeState.TOOLS_PARTIAL, resumePlan.turnState());
        assertEquals(1, resumePlan.toolsToExecute().size());
        assertEquals("tc-2", resumePlan.toolsToExecute().getFirst().id());

        var initialExecutions = toolkit.invocationCount("FOO");

        var result = runner.resumeAfterCrash(agent(runner), "do it", taskId, stepId, "work", List.of(), toolkitMap(toolkit), null, run, new AtomicBoolean(false), resumePlan);

        assertEquals(AgentStatus.COMPLETED, result.status());

        assertEquals(initialExecutions + 1, toolkit.invocationCount("FOO"),
                "Only the missing tool call (tc-2) should execute on resume — tc-1 was already completed");

        var finalLog = store.load(taskId);
        var reconciledTurn = finalLog.findTurnById(turnId);
        assertNotNull(reconciledTurn);
        assertEquals(ai.agentican.framework.state.TurnLog.State.COMPLETED, reconciledTurn.state(),
                "In-flight turn must be marked COMPLETED after resume reconciles the pending tools");
    }

    @Test
    void resumeResponseReceivedExecutesAllToolsWithoutReCallingLlm() {

        var store = new TaskStateStoreMemory();

        var toolkit = new MockToolkit(List.of(
                new ToolDefinition("BAR", "bar tool", Map.of())
        )).onExecute("BAR", "{\"done\":true}");

        var mockLlm = new MockLlmClient().onSend("", endTurn("Done after resume"));

        var runner = SmacAgentRunner.builder()
                .llmClient(mockLlm.toLlmClient())
                .taskStateStore(store)
                .maxIterations(3)
                .build();

        var taskId = "t-" + Ids.generate();
        var stepId = "s-" + Ids.generate();
        var runId = Ids.generate();
        var turnId = Ids.generate();

        store.taskStarted(taskId, "resume", null, Map.of());
        store.stepStarted(taskId, stepId, "work");
        store.runStarted(taskId, stepId, runId, "resume-agent");
        store.turnStarted(taskId, runId, turnId);

        store.messageSent(taskId, turnId,
                new ai.agentican.framework.llm.LlmRequest("sys", null, "u", List.of(), 0, "d", "a", "c"));

        var tc = new ToolCall("tc-1", "BAR", Map.of());
        var response = new ai.agentican.framework.llm.LlmResponse(
                "call bar", List.of(tc), StopReason.TOOL_USE, 1, 1, 0, 0, 0);
        store.responseReceived(taskId, turnId, response);

        var taskLog = store.load(taskId);
        var run = taskLog.findStepById(stepId).lastRun();

        var resumePlan = ResumeClassifier.classify(taskLog,
                ai.agentican.framework.orchestration.model.Plan.builder("p").description("")
                        .step(new ai.agentican.framework.orchestration.model.PlanStepAgent(
                                "work", "resume-agent", "do it", List.of(), false, List.of(), List.of()))
                        .build());

        assertEquals(TurnResumeState.RESPONSE_RECEIVED, resumePlan.turnState());

        var initialExecutions = toolkit.invocationCount("BAR");

        var result = runner.resumeAfterCrash(agent(runner), "do it", taskId, stepId, "work", List.of(), toolkitMap(toolkit), null, run, new AtomicBoolean(false), resumePlan);

        assertEquals(AgentStatus.COMPLETED, result.status());

        assertEquals(initialExecutions + 1, toolkit.invocationCount("BAR"),
                "BAR should execute exactly once via replay — no second LLM call was issued for this turn");
    }

    @Test
    void resumeMessageSentAbandonsTurnTokenPathLeavesNoWasteBeyondOneRequest() {

        var store = new TaskStateStoreMemory();

        var mockLlm = new MockLlmClient().onSend("", endTurn("Done after resume"));

        var runner = SmacAgentRunner.builder()
                .llmClient(mockLlm.toLlmClient())
                .taskStateStore(store)
                .maxIterations(3)
                .build();

        var taskId = "t-" + Ids.generate();
        var stepId = "s-" + Ids.generate();
        var runId = Ids.generate();
        var deadTurnId = Ids.generate();

        store.taskStarted(taskId, "resume", null, Map.of());
        store.stepStarted(taskId, stepId, "work");
        store.runStarted(taskId, stepId, runId, "resume-agent");
        store.turnStarted(taskId, runId, deadTurnId);
        store.messageSent(taskId, deadTurnId,
                new ai.agentican.framework.llm.LlmRequest("sys", null, "u", List.of(), 0, "d", "a", "c"));

        var taskLog = store.load(taskId);
        var run = taskLog.findStepById(stepId).lastRun();

        var resumePlan = ResumeClassifier.classify(taskLog,
                ai.agentican.framework.orchestration.model.Plan.builder("p").description("")
                        .step(new ai.agentican.framework.orchestration.model.PlanStepAgent(
                                "work", "resume-agent", "do it", List.of(), false, List.of(), List.of()))
                        .build());

        assertEquals(TurnResumeState.MESSAGE_SENT, resumePlan.turnState());

        var result = runner.resumeAfterCrash(agent(runner), "do it", taskId, stepId, "work", List.of(), Map.of(), null, run, new AtomicBoolean(false), resumePlan);

        assertEquals(AgentStatus.COMPLETED, result.status());

        var finalLog = store.load(taskId);
        var deadTurn = finalLog.findTurnById(deadTurnId);
        assertEquals(ai.agentican.framework.state.TurnLog.State.ABANDONED, deadTurn.state());
    }

    @Test
    void resumeClosedEndTurnShortCircuitsWithoutCallingLlm() {

        var store = new TaskStateStoreMemory();

        // MockLlmClient with zero entries — throws on any invocation. Short-circuit must skip LLM entirely.
        var mockLlm = new MockLlmClient();

        var runner = SmacAgentRunner.builder()
                .llmClient(mockLlm.toLlmClient())
                .taskStateStore(store)
                .maxIterations(3)
                .build();

        var taskId = "t-" + Ids.generate();
        var stepId = "s-" + Ids.generate();
        var runId = Ids.generate();
        var turnId = Ids.generate();

        store.taskStarted(taskId, "resume", null, Map.of());
        store.stepStarted(taskId, stepId, "work");
        store.runStarted(taskId, stepId, runId, "resume-agent");
        store.turnStarted(taskId, runId, turnId);
        store.messageSent(taskId, turnId,
                new ai.agentican.framework.llm.LlmRequest("sys", null, "u", List.of(), 0, "d", "a", "c"));

        // Response arrived with END_TURN, no tool calls — turn was logically complete pre-crash.
        var response = new ai.agentican.framework.llm.LlmResponse(
                "all done", List.of(), StopReason.END_TURN, 1, 1, 0, 0, 0);
        store.responseReceived(taskId, turnId, response);
        store.turnCompleted(taskId, turnId);

        var taskLog = store.load(taskId);
        var run = taskLog.findStepById(stepId).lastRun();

        var resumePlan = ResumeClassifier.classify(taskLog,
                ai.agentican.framework.orchestration.model.Plan.builder("p").description("")
                        .step(new ai.agentican.framework.orchestration.model.PlanStepAgent(
                                "work", "resume-agent", "do it", List.of(), false, List.of(), List.of()))
                        .build());

        assertEquals(TurnResumeState.CLOSED, resumePlan.turnState());

        var result = runner.resumeAfterCrash(agent(runner), "do it", taskId, stepId, "work", List.of(), Map.of(), null, run, new AtomicBoolean(false), resumePlan);

        assertEquals(AgentStatus.COMPLETED, result.status(),
                "CLOSED state with END_TURN should short-circuit to COMPLETED without calling the LLM");
    }
}
