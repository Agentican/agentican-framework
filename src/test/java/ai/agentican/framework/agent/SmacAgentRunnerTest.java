package ai.agentican.framework.agent;

import ai.agentican.framework.MockLlmClient;
import ai.agentican.framework.MockToolkit;
import ai.agentican.framework.hitl.AskQuestionToolkit;
import ai.agentican.framework.hitl.HitlCheckpointType;
import ai.agentican.framework.hitl.HitlManager;
import ai.agentican.framework.hitl.HitlNotifier;
import ai.agentican.framework.hitl.HitlResponse;
import ai.agentican.framework.knowledge.Fact;
import ai.agentican.framework.knowledge.KnowledgeEntry;
import ai.agentican.framework.knowledge.KnowledgeStatus;
import ai.agentican.framework.knowledge.KnowledgeToolkit;
import ai.agentican.framework.knowledge.MemKnowledgeStore;
import ai.agentican.framework.llm.LlmClient;
import ai.agentican.framework.llm.LlmResponse;
import ai.agentican.framework.llm.StopReason;
import ai.agentican.framework.llm.ToolCall;
import ai.agentican.framework.tools.ToolDefinition;
import ai.agentican.framework.tools.ToolRecord;
import ai.agentican.framework.tools.ToolResult;
import ai.agentican.framework.tools.Toolkit;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static ai.agentican.framework.MockLlmClient.*;
import static org.junit.jupiter.api.Assertions.*;

class SmacAgentRunnerTest {

    private HitlManager autoApproveHitl() {

        return new HitlManager((mgr, cp) -> mgr.respond(cp.id(), HitlResponse.approve()));
    }

    private HitlManager loggingHitl() {

        return new HitlManager(HitlNotifier.logging());
    }

    private Agent agent(SmacAgentRunner runner) {

        return new Agent("test-agent", "Test role", List.of(), runner);
    }

    private Map<String, Toolkit> toolkitMap(MockToolkit toolkit) {

        var map = new LinkedHashMap<String, Toolkit>();

        for (var def : toolkit.toolDefinitions())
            map.put(def.name(), toolkit);

        return map;
    }

    @Test
    void completesWithNoTools() {

        var mockLlm = new MockLlmClient()
                .onSend("", endTurn("Hello world"));

        var runner = SmacAgentRunner.builder()
                .llmClient(mockLlm.toLlmClient())
                .hitlManager(autoApproveHitl())
                .maxIterations(5)
                .build();

        var result = agent(runner).run("Do something", List.of(), Map.of(), "test-task", "step-id", "test-step");

        assertEquals(AgentStatus.COMPLETED, result.status());
        assertEquals("Hello world", result.text());
        assertEquals(1, result.run().turns().size());
    }

    @Test
    void executesToolAndContinues() {

        var mockLlm = new MockLlmClient()
                .onSend("", toolUse("thinking", "MY_TOOL", Map.of("q", "test")))
                .onSend("tool-result-MY_TOOL", endTurn("Done"));

        var toolkit = new MockToolkit(List.of(
                new ToolDefinition("MY_TOOL", "A test tool", Map.of())))
                .onExecute("MY_TOOL", "{\"result\": \"found\"}");

        var runner = SmacAgentRunner.builder()
                .llmClient(mockLlm.toLlmClient())
                .hitlManager(autoApproveHitl())
                .maxIterations(5)
                .build();

        var result = agent(runner).run("Do something", List.of(), toolkitMap(toolkit), "test-task", "step-id", "test-step");

        assertEquals(AgentStatus.COMPLETED, result.status());
        assertEquals("Done", result.text());
        assertEquals(2, result.run().turns().size());
    }

    @Test
    void suspendsOnApprovalTool() {

        var mockLlm = new MockLlmClient()
                .onSend("", toolUse("calling", "HITL_TOOL", Map.of()));

        var toolkit = new MockToolkit(List.of(
                new ToolDefinition("HITL_TOOL", "Requires approval", Map.of())))
                .withHitl("HITL_TOOL")
                .onExecute("HITL_TOOL", "{\"ok\": true}");

        var runner = SmacAgentRunner.builder()
                .llmClient(mockLlm.toLlmClient())
                .hitlManager(loggingHitl())
                .maxIterations(5)
                .build();

        var result = agent(runner).run("Do something", List.of(), toolkitMap(toolkit), "test-task", "step-id", "test-step");

        assertEquals(AgentStatus.SUSPENDED, result.status());
        assertNotNull(result.checkpoint());
        assertEquals(HitlCheckpointType.TOOL_CALL, result.checkpoint().type());
    }

    @Test
    void suspendsOnQuestionTool() {

        var mockLlm = new MockLlmClient()
                .onSend("", toolUse("asking", "ASK_QUESTION", Map.of("question", "What color?")));

        var askToolkit = new AskQuestionToolkit();

        var toolkits = new LinkedHashMap<String, Toolkit>();
        toolkits.put("ASK_QUESTION", askToolkit);

        var runner = SmacAgentRunner.builder()
                .llmClient(mockLlm.toLlmClient())
                .hitlManager(loggingHitl())
                .maxIterations(5)
                .build();

        var result = agent(runner).run("Do something", List.of(), toolkits, "test-task", "step-id", "test-step");

        assertEquals(AgentStatus.SUSPENDED, result.status());
        assertNotNull(result.checkpoint());
        assertEquals(HitlCheckpointType.QUESTION, result.checkpoint().type());
        assertEquals("What color?", result.checkpoint().description());
    }

    @Test
    void executesNonHitlToolsBeforeSuspending() {

        // Create a response with two tool calls: one normal, one HITL
        var response = new LlmResponse("processing", List.of(
                new ToolCall("call-normal", "NORMAL_TOOL", Map.of()),
                new ToolCall("call-hitl", "HITL_TOOL", Map.of())),
                StopReason.TOOL_USE, 0, 0, 0, 0, 0);

        var mockLlm = new MockLlmClient()
                .onSend("", response);

        var toolkit = new MockToolkit(List.of(
                new ToolDefinition("NORMAL_TOOL", "Normal tool", Map.of()),
                new ToolDefinition("HITL_TOOL", "Requires approval", Map.of())))
                .withHitl("HITL_TOOL")
                .onExecute("NORMAL_TOOL", "{\"data\": \"ok\"}")
                .onExecute("HITL_TOOL", "{\"done\": true}");

        var runner = SmacAgentRunner.builder()
                .llmClient(mockLlm.toLlmClient())
                .hitlManager(loggingHitl())
                .maxIterations(5)
                .build();

        var result = agent(runner).run("Do something", List.of(), toolkitMap(toolkit), "test-task", "step-id", "test-step");

        assertEquals(AgentStatus.SUSPENDED, result.status());
        // The turn should have 1 tool result (the normal tool was executed)
        assertEquals(1, result.run().turns().getFirst().toolResults().size());
        assertEquals("NORMAL_TOOL", result.run().turns().getFirst().toolResults().getFirst().toolName());
    }

    @Test
    void resumeAfterApproval() {

        var mockLlm = new MockLlmClient()
                .onSend("", toolUse("calling", "HITL_TOOL", Map.of()))
                .onSend("tool-result-HITL_TOOL", endTurn("Resumed successfully"));

        var toolkit = new MockToolkit(List.of(
                new ToolDefinition("HITL_TOOL", "Requires approval", Map.of())))
                .withHitl("HITL_TOOL")
                .onExecute("HITL_TOOL", "{\"ok\": true}");

        var hitlManager = loggingHitl();

        var runner = SmacAgentRunner.builder()
                .llmClient(mockLlm.toLlmClient())
                .hitlManager(hitlManager)
                .maxIterations(5)
                .build();

        var agentInstance = agent(runner);

        // First call: suspends
        var suspended = agentInstance.run("Do something", List.of(), toolkitMap(toolkit), "test-task", "step-id", "test-step");

        assertEquals(AgentStatus.SUSPENDED, suspended.status());

        // Resume with approval (empty hitlToolResults means approved)
        var resumed = runner.resume(agentInstance, "Do something", List.of(),
                suspended.run(), List.of(), toolkitMap(toolkit), "test-task", "step-id", "test-step");

        assertEquals(AgentStatus.COMPLETED, resumed.status());
        assertEquals("Resumed successfully", resumed.text());
    }

    @Test
    void resumeAfterRejection() {

        var mockLlm = new MockLlmClient()
                .onSend("", toolUse("calling", "HITL_TOOL", Map.of()))
                .onSend("rejected", endTurn("OK, I won't do that"));

        var toolkit = new MockToolkit(List.of(
                new ToolDefinition("HITL_TOOL", "Requires approval", Map.of())))
                .withHitl("HITL_TOOL")
                .onExecute("HITL_TOOL", "{\"ok\": true}");

        var hitlManager = loggingHitl();

        var runner = SmacAgentRunner.builder()
                .llmClient(mockLlm.toLlmClient())
                .hitlManager(hitlManager)
                .maxIterations(5)
                .build();

        var agentInstance = agent(runner);

        // First call: suspends
        var suspended = agentInstance.run("Do something", List.of(), toolkitMap(toolkit), "test-task", "step-id", "test-step");

        assertEquals(AgentStatus.SUSPENDED, suspended.status());

        // Resume with rejection
        var rejectionResult = new ToolResult("hitl", "HITL_TOOL",
                "{\"error\": \"Tool call rejected\", \"feedback\": \"Not allowed\"}");

        var resumed = runner.resume(agentInstance, "Do something", List.of(),
                suspended.run(), List.of(rejectionResult), toolkitMap(toolkit), "test-task", "step-id", "test-step");

        assertEquals(AgentStatus.COMPLETED, resumed.status());
        assertEquals("OK, I won't do that", resumed.text());
    }

    @Test
    void maxTurnsExceeded() {

        var mockLlm = new MockLlmClient()
                .onSend("", toolUse("turn0", "MY_TOOL", Map.of()))
                .onSend("", toolUse("turn1", "MY_TOOL", Map.of()))
                .onSend("", toolUse("turn2", "MY_TOOL", Map.of()));

        var toolkit = new MockToolkit(List.of(
                new ToolDefinition("MY_TOOL", "A tool", Map.of())))
                .onExecute("MY_TOOL", "{\"ok\": true}");

        var runner = SmacAgentRunner.builder()
                .llmClient(mockLlm.toLlmClient())
                .hitlManager(autoApproveHitl())
                .maxIterations(3)
                .build();

        var result = agent(runner).run("Do something", List.of(), toolkitMap(toolkit), "test-task", "step-id", "test-step");

        assertEquals(AgentStatus.MAX_TURNS, result.status());
        assertEquals(3, result.run().turns().size());
    }

    @Test
    void toolExecutionFailure() {

        var mockLlm = new MockLlmClient()
                .onSend("", toolUse("calling", "FAILING_TOOL", Map.of()))
                .onSend("error", endTurn("Handled error"));

        // Create a toolkit that throws on execute via the Toolkit interface directly
        Toolkit toolkit = new Toolkit() {

            private final ToolRecord tool = new ToolRecord("FAILING_TOOL", "A failing tool", Map.of());

            @Override
            public List<ai.agentican.framework.tools.Tool> tools() { return List.of(tool); }

            @Override
            public boolean handles(String toolName) { return "FAILING_TOOL".equals(toolName); }

            @Override
            public String execute(String toolName, Map<String, Object> arguments) throws Exception {
                throw new RuntimeException("Something went wrong");
            }
        };

        var runner = SmacAgentRunner.builder()
                .llmClient(mockLlm.toLlmClient())
                .hitlManager(autoApproveHitl())
                .maxIterations(5)
                .build();

        var toolkits = new LinkedHashMap<String, Toolkit>();
        toolkits.put("FAILING_TOOL", toolkit);

        var result = agent(runner).run("Do something", List.of(), toolkits, "test-task", "step-id", "test-step");

        assertEquals(AgentStatus.COMPLETED, result.status());
        assertEquals("Handled error", result.text());
    }

    @Test
    void knowledgeIndexInjectedInUserMessage() {

        var store = new MemKnowledgeStore();
        var entry = new KnowledgeEntry("k1", "Customer Pricing Data", "Pricing info");

        entry.setStatus(KnowledgeStatus.INDEXED);
        store.save(entry);

        // Match on the entry name that should appear in the injected knowledge index
        var mockLlm = new MockLlmClient()
                .onSend("Customer Pricing Data", endTurn("done"));

        var runner = SmacAgentRunner.builder()
                .llmClient(mockLlm.toLlmClient())
                .hitlManager(autoApproveHitl())
                .knowledgeStore(store)
                .maxIterations(5)
                .build();

        var result = agent(runner).run("Do something", List.of(), Map.of(), "test-task", "step-id", "test-step");

        assertEquals(AgentStatus.COMPLETED, result.status());
        assertEquals("done", result.text());
    }

    @Test
    void recallKnowledgeReturnsFacts() {

        var store = new MemKnowledgeStore();
        var entry = new KnowledgeEntry("k1", "Customer Pricing Data", "Pricing info");

        entry.addFact(Fact.of("Monthly Cost", "$10/month", List.of("pricing")));
        entry.setStatus(KnowledgeStatus.INDEXED);
        store.save(entry);

        var mockLlm = new MockLlmClient()
                .onSend("Customer Pricing Data",
                        toolUse("recalling", KnowledgeToolkit.TOOL_NAME,
                                Map.of("entry_ids", List.of("k1"))))
                .onSend("tool-result-" + KnowledgeToolkit.TOOL_NAME, endTurn("got the facts"));

        var runner = SmacAgentRunner.builder()
                .llmClient(mockLlm.toLlmClient())
                .hitlManager(autoApproveHitl())
                .knowledgeStore(store)
                .maxIterations(5)
                .build();

        var result = agent(runner).run("Do something", List.of(), Map.of(), "test-task", "step-id", "test-step");

        assertEquals(AgentStatus.COMPLETED, result.status());
        assertEquals("got the facts", result.text());
        assertEquals(2, result.run().turns().size());
    }

    @Test
    void timeoutExceeded() {

        // LLM that sleeps 100ms and returns a tool use, forcing a second loop iteration
        var slowLlm = (LlmClient) request -> {
            try { Thread.sleep(100); } catch (InterruptedException _) {}
            return new LlmResponse("thinking", List.of(new ToolCall("t1", "MY_TOOL", Map.of())),
                    StopReason.TOOL_USE, 0, 0, 0, 0, 0);
        };

        var toolkit = new MockToolkit(List.of(new ToolDefinition("MY_TOOL", "test", Map.of())))
                .onExecute("MY_TOOL", "{\"ok\": true}");

        var runner = SmacAgentRunner.builder()
                .llmClient(slowLlm)
                .hitlManager(loggingHitl())
                .timeout(Duration.ofMillis(10))
                .build();

        var result = agent(runner).run("task", List.of(), toolkitMap(toolkit), "test-task", "step-id", "step");

        assertEquals(AgentStatus.TIMED_OUT, result.status());
    }
}
