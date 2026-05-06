package ai.agentican.framework.agent;

import ai.agentican.framework.config.AgentConfig;
import ai.agentican.framework.llm.LlmClient;
import ai.agentican.framework.llm.LlmRequest;
import ai.agentican.framework.llm.LlmResponse;
import ai.agentican.framework.llm.Message;
import ai.agentican.framework.llm.StopReason;
import ai.agentican.framework.llm.ToolCall;
import ai.agentican.framework.store.WorkflowRunStoreMemory;
import ai.agentican.framework.hitl.HitlType;
import ai.agentican.framework.tools.Tool;
import ai.agentican.framework.tools.Toolkit;
import ai.agentican.framework.util.Ids;

import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ReActAgentRunnerTest {

    @Test
    void completesImmediatelyWhenLlmReturnsTextOnly() {

        var captured = new java.util.ArrayList<LlmRequest>();
        var llm = recordingLlm(captured, endTurn("done"));
        var runner = newRunner(llm, 5);

        var result = runRunner(runner, "Say done", Map.of());

        assertEquals(AgentStatus.COMPLETED, result.status());
        assertEquals(1, captured.size(), "single LLM round-trip");

        var msgs = captured.get(0).messages();
        assertEquals(1, msgs.size(), "history starts with user task only");
        assertEquals(Message.Role.USER, msgs.get(0).role());
        assertInstanceOf(Message.TextBlock.class, msgs.get(0).blocks().get(0));
    }

    @Test
    void loopsViaNativeToolUseAndResultBlocks() {

        var captured = new java.util.ArrayList<LlmRequest>();

        var llm = recordingLlm(captured,
                toolUse("calling tool", "echo", Map.of("text", "hi")),
                endTurn("final answer"));

        var runner = newRunner(llm, 5);

        var result = runRunner(runner, "Echo something", Map.of("echo", new EchoToolkit()));

        assertEquals(AgentStatus.COMPLETED, result.status());
        assertEquals(2, captured.size(), "two round-trips: tool_use then final");

        // Turn 0: history = [user(task)]
        var turn0 = captured.get(0).messages();
        assertEquals(1, turn0.size());
        assertEquals(Message.Role.USER, turn0.get(0).role());

        // Turn 1: history = [user(task), assistant(text+tool_use), user(tool_result)]
        var turn1 = captured.get(1).messages();
        assertEquals(3, turn1.size(), "history grows with assistant + tool_result messages");
        assertEquals(Message.Role.USER, turn1.get(0).role());
        assertEquals(Message.Role.ASSISTANT, turn1.get(1).role());
        assertEquals(Message.Role.USER, turn1.get(2).role());

        var assistantBlocks = turn1.get(1).blocks();
        assertTrue(assistantBlocks.stream().anyMatch(b -> b instanceof Message.TextBlock),
                "assistant message preserves text");
        assertTrue(assistantBlocks.stream().anyMatch(b -> b instanceof Message.ToolUseBlock),
                "assistant message preserves tool_use block");

        var toolResultBlocks = turn1.get(2).blocks();
        assertEquals(1, toolResultBlocks.size());
        var tr = (Message.ToolResultBlock) toolResultBlocks.get(0);
        assertFalse(tr.isError());
        assertTrue(tr.content().contains("hi"), "tool result content reaches the next turn");

        // Verify tool_use_id ↔ tool_result.toolUseId linkage
        var toolUse = (Message.ToolUseBlock) assistantBlocks.stream()
                .filter(b -> b instanceof Message.ToolUseBlock).findFirst().orElseThrow();
        assertEquals(toolUse.id(), tr.toolUseId(), "tool_result references tool_use id");
    }

    @Test
    void returnsMaxTurnsWhenLlmKeepsCallingTools() {

        var llm = recordingLlm(new java.util.ArrayList<>(),
                toolUse("loop 0", "echo", Map.of("text", "0")),
                toolUse("loop 1", "echo", Map.of("text", "1")),
                toolUse("loop 2", "echo", Map.of("text", "2")));

        var runner = newRunner(llm, 2);

        var result = runRunner(runner, "Loop forever", Map.of("echo", new EchoToolkit()));

        assertEquals(AgentStatus.MAX_TURNS, result.status());
    }

    private static AgentResult runRunner(AgentRunner runner, String task, Map<String, Toolkit> toolkits) {

        var agent = Agent.builder()
                .config(new AgentConfig(null, "TestAgent", "An agent for tests", null, null, null, null))
                .runner(runner)
                .build();

        return runner.run(agent, task, Ids.generate(), Ids.generate(), "step",
                null, List.of(), toolkits, null);
    }

    private static ReActAgentRunner newRunner(LlmClient llm, int maxTurns) {

        return ReActAgentRunner.builder()
                .llmClient(llm)
                .llmName("default")
                .llmProvider("anthropic")
                .llmModel("claude-test")
                .maxIterations(maxTurns)
                .workflowRunStore(new WorkflowRunStoreMemory())
                .build();
    }

    private static LlmClient recordingLlm(List<LlmRequest> captured, LlmResponse... responses) {

        var queue = new ArrayDeque<>(List.of(responses));

        return (LlmClient) request -> {
            captured.add(request);
            if (queue.isEmpty())
                throw new IllegalStateException("Mock ran out of canned responses");
            return queue.poll();
        };
    }

    private static LlmResponse endTurn(String text) {

        return new LlmResponse(text, List.of(), StopReason.END_TURN, 0, 0, 0, 0, 0);
    }

    private static LlmResponse toolUse(String text, String toolName, Map<String, Object> args) {

        return new LlmResponse(text, List.of(new ToolCall("mock-" + toolName + "-" + System.nanoTime(),
                toolName, args)), StopReason.TOOL_USE, 0, 0, 0, 0, 0);
    }

    /** Minimal toolkit that echoes its "text" arg back. */
    private static final class EchoToolkit implements Toolkit {

        @Override public String displayName() { return "echo"; }

        @Override public List<Tool> tools() { return List.of(new EchoTool()); }

        @Override public boolean handles(String toolName) { return "echo".equals(toolName); }

        @Override public String execute(String toolName, Map<String, Object> arguments) {
            return "echoed: " + arguments.getOrDefault("text", "");
        }
    }

    private static final class EchoTool implements Tool {

        @Override public String name() { return "echo"; }

        @Override public String description() { return "Echo input"; }

        @Override public Map<String, Object> properties() { return Map.of(); }

        @Override public List<String> required() { return List.of(); }

        @Override public HitlType hitlType() { return HitlType.NONE; }
    }
}
