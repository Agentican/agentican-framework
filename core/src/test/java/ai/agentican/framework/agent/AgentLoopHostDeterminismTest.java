package ai.agentican.framework.agent;

import ai.agentican.framework.MockLlmClient;
import ai.agentican.framework.MockToolkit;
import ai.agentican.framework.config.AgentConfig;
import ai.agentican.framework.hitl.HitlCheckpoint;
import ai.agentican.framework.hitl.HitlManager;
import ai.agentican.framework.hitl.HitlResponse;
import ai.agentican.framework.knowledge.KnowledgeEntry;
import ai.agentican.framework.llm.LlmRequest;
import ai.agentican.framework.llm.LlmResponse;
import ai.agentican.framework.llm.ToolCall;
import ai.agentican.framework.orchestration.model.WorkflowDefinition;
import ai.agentican.framework.state.WorkflowRunLog;
import ai.agentican.framework.store.WorkflowRunStoreMemory;
import ai.agentican.framework.tools.ToolDefinition;
import ai.agentican.framework.tools.ToolResult;
import ai.agentican.framework.tools.Toolkit;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static ai.agentican.framework.MockLlmClient.endTurn;
import static ai.agentican.framework.MockLlmClient.toolUse;
import static org.junit.jupiter.api.Assertions.*;
import ai.agentican.framework.agent.AgentStatus;
import ai.agentican.framework.event.AgenticanEventBus;
import ai.agentican.framework.event.WorkflowRunStorePersister;
import ai.agentican.framework.llm.TokenUsage;

/**
 * Determinism harness for the {@link AgentLoopHost} SPI. Runs an agent loop
 * twice — first via a {@link RecordingAgentLoopHost} wrapping a real
 * {@link InProcessAgentLoopHost}, then via a {@link ReplayingAgentLoopHost}
 * driven by the recorded values — and asserts the runner's externally-visible
 * output is identical across both runs.
 *
 * <p>If any future change introduces non-determinism inside the runner
 * (a stray {@code Instant.now()}, an unordered map iteration that affects
 * output, etc.), this test fails. It is the in-process analogue of Temporal's
 * workflow replay safety check.
 */
class AgentLoopHostDeterminismTest {

    // ── Tests ───────────────────────────────────────────────────────────────

    @Test
    void smacAgentRunnerDeterministicAcrossRecordAndReplay() {

        // A 2-turn loop: one tool use, then a terminal response.
        var llm1 = new MockLlmClient()
                .onSend("", toolUse("thinking", "TEST_TOOL", Map.of("q", "alpha")))
                .onSend("<name>TEST_TOOL</name>", endTurn("All done"));

        var toolkit1 = new MockToolkit(List.of(new ToolDefinition("TEST_TOOL", "Test", Map.of())))
                .onExecute("TEST_TOOL", "{\"result\":\"ok\"}");

        var runner1 = SmacAgentRunner.builder()
                .llmClient(llm1.toLlmClient())
                .hitlManager(autoApproveHitl())
                .maxIterations(5)
                .build();

        var hostStore1 = new WorkflowRunStoreMemory();
        var hostBus1 = new AgenticanEventBus();
        hostBus1.subscribeFirst(new WorkflowRunStorePersister(hostStore1));

        var realHost1 = new InProcessAgentLoopHost(llm1.toLlmClient(), hostStore1, hostBus1,
                autoApproveHitl(), null, null);

        var recording = new RecordingAgentLoopHost(realHost1);

        var firstResult = runner1.run(smacAgent(runner1), "Do something", "task-1", "step-1", "step",
                null, List.of(), toolkitMap(toolkit1), null, recording);

        assertEquals(AgentStatus.COMPLETED, firstResult.status());
        assertEquals("All done", firstResult.text());

        // Replay against the recording — the second run never touches a real LLM or store.
        var replayHost = new ReplayingAgentLoopHost(recording.recordings());

        // Use a freshly-built runner with a never-called LlmClient — proves the replay drives the loop.
        var unusedLlm = new MockLlmClient();
        var runner2 = SmacAgentRunner.builder()
                .llmClient(unusedLlm.toLlmClient())
                .hitlManager(autoApproveHitl())
                .maxIterations(5)
                .build();

        var secondResult = runner2.run(smacAgent(runner2), "Do something", "task-1", "step-1", "step",
                null, List.of(), toolkitMap(toolkit1), null, replayHost);

        // Externally-visible outputs must match exactly.
        assertEquals(firstResult.status(),                secondResult.status());
        assertEquals(firstResult.text(),                  secondResult.text());
        assertEquals(firstResult.run().turns().size(),    secondResult.run().turns().size());
    }

    @Test
    void reActAgentRunnerDeterministicAcrossRecordAndReplay() {

        // ReAct stores conversation history in LlmRequest.messages, which MockLlmClient doesn't inspect.
        // Empty-string matchers + once-consumed entries give us the right sequence anyway.
        var llm1 = new MockLlmClient()
                .onSend("", toolUse("react-think", "REACT_TOOL", Map.of("k", "v")))
                .onSend("", endTurn("Final ReAct answer"));

        var toolkit1 = new MockToolkit(List.of(new ToolDefinition("REACT_TOOL", "ReAct test", Map.of())))
                .onExecute("REACT_TOOL", "{\"value\":42}");

        var runner1 = ReActAgentRunner.builder()
                .llmClient(llm1.toLlmClient())
                .maxIterations(5)
                .build();

        var hostStore = new WorkflowRunStoreMemory();
        var hostBus = new AgenticanEventBus();
        hostBus.subscribeFirst(new WorkflowRunStorePersister(hostStore));

        var realHost = new InProcessAgentLoopHost(llm1.toLlmClient(), hostStore, hostBus, null, null, null);

        var recording = new RecordingAgentLoopHost(realHost);

        var firstResult = runner1.run(reActAgent(runner1), "Solve it", "task-r", "step-r", "step",
                null, List.of(), toolkitMap(toolkit1), null, recording);

        assertEquals(AgentStatus.COMPLETED, firstResult.status());
        assertEquals("Final ReAct answer", firstResult.text());

        var replayHost = new ReplayingAgentLoopHost(recording.recordings());

        var runner2 = ReActAgentRunner.builder()
                .llmClient(new MockLlmClient().toLlmClient())   // intentionally empty — never called
                .maxIterations(5)
                .build();

        var secondResult = runner2.run(reActAgent(runner2), "Solve it", "task-r", "step-r", "step",
                null, List.of(), toolkitMap(toolkit1), null, replayHost);

        assertEquals(firstResult.status(), secondResult.status());
        assertEquals(firstResult.text(),   secondResult.text());
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private HitlManager autoApproveHitl() {

        return new HitlManager((mgr, cp) -> mgr.respond(cp.id(), HitlResponse.approve()));
    }

    private Agent smacAgent(SmacAgentRunner runner) {

        return Agent.builder()
                .config(AgentConfig.builder().name("test-agent").id("test-agent").role("Test role").build())
                .runner(runner)
                .build();
    }

    private Agent reActAgent(ReActAgentRunner runner) {

        return Agent.builder()
                .config(AgentConfig.builder().name("react-agent").id("react-agent").role("ReAct role").build())
                .runner(runner)
                .build();
    }

    private Map<String, Toolkit> toolkitMap(MockToolkit toolkit) {

        var map = new LinkedHashMap<String, Toolkit>();

        for (var def : toolkit.toolDefinitions())
            map.put(def.name(), toolkit);

        return map;
    }

    // ── Recording host ──────────────────────────────────────────────────────

    /**
     * Wraps a real {@link AgentLoopHost} and records every value-returning call
     * for later replay. Void store-event methods are passed through unchanged —
     * they don't affect the loop's branching and don't need to be recorded.
     */
    static final class RecordingAgentLoopHost implements AgentLoopHost {

        record Entry(String method, Object value) { }

        private final AgentLoopHost delegate;
        private final List<Entry> entries = new ArrayList<>();

        RecordingAgentLoopHost(AgentLoopHost delegate) { this.delegate = delegate; }

        List<Entry> recordings() { return List.copyOf(entries); }

        private <T> T capture(String method, T value) {
            entries.add(new Entry(method, value));
            return value;
        }

        // ── value-returning methods (recorded) ─────────────────────────────

        @Override public LlmResponse  callLlm(LlmRequest r)                                       { return capture("callLlm", delegate.callLlm(r)); }
        @Override public String       executeTool(String n, Map<String,Object> a, Toolkit t)      { return capture("executeTool", delegate.executeTool(n, a, t)); }
        @Override public Instant      now()                                                       { return capture("now", delegate.now()); }
        @Override public String       newId()                                                     { return capture("newId", delegate.newId()); }
        @Override public WorkflowRunLog loadRunLog(String taskId)                                 { return capture("loadRunLog", delegate.loadRunLog(taskId)); }
        @Override public HitlCheckpoint createToolApprovalCheckpoint(ToolCall c, String s)        { return capture("createToolApprovalCheckpoint", delegate.createToolApprovalCheckpoint(c, s)); }
        @Override public HitlCheckpoint createQuestionCheckpoint(String q, String c, String s)    { return capture("createQuestionCheckpoint", delegate.createQuestionCheckpoint(q, c, s)); }
        @Override public HitlResponse awaitHitlResponse(HitlCheckpoint c)                         { return capture("awaitHitlResponse", delegate.awaitHitlResponse(c)); }
        @Override public KnowledgeEntry knowledgeEntry(String id)                                 { return capture("knowledgeEntry", delegate.knowledgeEntry(id)); }
        @Override public boolean      isCancelled()                                               { return capture("isCancelled", delegate.isCancelled()); }

        // ── void methods (passed through, not recorded) ────────────────────

        @Override public void taskStarted(String t, String n, WorkflowDefinition p, Map<String, String> ps) { delegate.taskStarted(t, n, p, ps); }
        @Override public void stepStarted(String t, String si, String sn)             { delegate.stepStarted(t, si, sn); }
        @Override public void runStarted(String t, String si, String r, String an)    { delegate.runStarted(t, si, r, an); }
        @Override public void runCompleted(String t, String si, String r, AgentStatus st, TokenUsage u) { delegate.runCompleted(t, si, r, st, u); }
        @Override public void turnStarted(String t, String r, String tu, int i)        { delegate.turnStarted(t, r, tu, i); }
        @Override public void turnCompleted(String t, String tu, int i, TokenUsage u) { delegate.turnCompleted(t, tu, i, u); }
        @Override public void turnAbandoned(String t, String tu)                      { delegate.turnAbandoned(t, tu); }
        @Override public void messageSent(String t, String tu, LlmRequest r)          { delegate.messageSent(t, tu, r); }
        @Override public void responseReceived(String t, String tu, LlmResponse r)    { delegate.responseReceived(t, tu, r); }
        @Override public void toolCallStarted(String t, String tu, ToolCall c)        { delegate.toolCallStarted(t, tu, c); }
        @Override public void toolCallCompleted(String t, String tu, ToolResult r)    { delegate.toolCallCompleted(t, tu, r); }
        @Override public void hitlNotified(String t, String si, HitlCheckpoint c)     { delegate.hitlNotified(t, si, c); }
    }

    // ── Replaying host ──────────────────────────────────────────────────────

    /**
     * Drives the agent loop by replaying recorded host return values in order.
     * If the runner asks for a value-returning method whose name doesn't match
     * the next recording, the replay fails fast — that's how non-determinism
     * surfaces as a test failure.
     *
     * <p>Void methods are no-ops on replay (they don't affect the loop's flow).
     */
    static final class ReplayingAgentLoopHost implements AgentLoopHost {

        private final Deque<RecordingAgentLoopHost.Entry> queue;

        ReplayingAgentLoopHost(List<RecordingAgentLoopHost.Entry> recordings) {
            this.queue = new ArrayDeque<>(recordings);
        }

        @SuppressWarnings("unchecked")
        private <T> T next(String method) {
            var entry = queue.poll();
            if (entry == null)
                throw new AssertionError("Replay exhausted before " + method + " — runner diverged from recording");
            if (!entry.method().equals(method))
                throw new AssertionError("Replay expected " + entry.method() + " but runner called " + method + " — non-deterministic call sequence");
            return (T) entry.value();
        }

        // ── value-returning methods ────────────────────────────────────────

        @Override public LlmResponse    callLlm(LlmRequest r)                                       { return next("callLlm"); }
        @Override public String         executeTool(String n, Map<String,Object> a, Toolkit t)      { return next("executeTool"); }
        @Override public Instant        now()                                                       { return next("now"); }
        @Override public String         newId()                                                     { return next("newId"); }
        @Override public WorkflowRunLog loadRunLog(String taskId)                                   { return next("loadRunLog"); }
        @Override public HitlCheckpoint createToolApprovalCheckpoint(ToolCall c, String s)          { return next("createToolApprovalCheckpoint"); }
        @Override public HitlCheckpoint createQuestionCheckpoint(String q, String c, String s)      { return next("createQuestionCheckpoint"); }
        @Override public HitlResponse   awaitHitlResponse(HitlCheckpoint c)                         { return next("awaitHitlResponse"); }
        @Override public KnowledgeEntry knowledgeEntry(String id)                                   { return next("knowledgeEntry"); }
        @Override public boolean        isCancelled()                                               { return next("isCancelled"); }

        // ── void methods (no-ops) ──────────────────────────────────────────

        @Override public void taskStarted(String t, String n, WorkflowDefinition p, Map<String, String> ps) { }
        @Override public void stepStarted(String t, String si, String sn)             { }
        @Override public void runStarted(String t, String si, String r, String an)    { }
        @Override public void runCompleted(String t, String si, String r, AgentStatus st, TokenUsage u) { }
        @Override public void turnStarted(String t, String r, String tu, int i)        { }
        @Override public void turnCompleted(String t, String tu, int i, TokenUsage u) { }
        @Override public void turnAbandoned(String t, String tu)                      { }
        @Override public void messageSent(String t, String tu, LlmRequest r)          { }
        @Override public void responseReceived(String t, String tu, LlmResponse r)    { }
        @Override public void toolCallStarted(String t, String tu, ToolCall c)        { }
        @Override public void toolCallCompleted(String t, String tu, ToolResult r)    { }
        @Override public void hitlNotified(String t, String si, HitlCheckpoint c)     { }
    }
}
