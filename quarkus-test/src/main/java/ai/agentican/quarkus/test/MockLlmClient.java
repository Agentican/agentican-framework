package ai.agentican.quarkus.test;

import ai.agentican.framework.llm.LlmClient;
import ai.agentican.framework.llm.LlmRequest;
import ai.agentican.framework.llm.LlmResponse;
import ai.agentican.framework.llm.StopReason;
import ai.agentican.framework.llm.ToolCall;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

@Singleton
@Named("default")
public class MockLlmClient implements LlmClient {

    private final Queue<LlmResponse> responses = new ConcurrentLinkedQueue<>();

    public MockLlmClient queueEndTurn(String text) {

        responses.add(LlmResponse.of(text, List.of(), StopReason.END_TURN, 100, 50, 10, 5, 0));
        return this;
    }

    public MockLlmClient queueToolCall(String callId, String toolName, Map<String, Object> args) {

        var toolCall = new ToolCall(callId, toolName, args);
        responses.add(LlmResponse.of("", List.of(toolCall), StopReason.TOOL_USE, 100, 50, 0, 0, 0));
        return this;
    }

    public void reset() {

        responses.clear();
    }

    @Override
    public LlmResponse send(LlmRequest request) {

        var response = responses.poll();

        if (response == null)
            return LlmResponse.of("[mock: no queued response]", List.of(), StopReason.END_TURN, 10, 10, 0, 0, 0);

        return response;
    }
}
