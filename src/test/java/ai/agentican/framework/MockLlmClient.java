package ai.agentican.framework;

import ai.agentican.framework.llm.LlmClient;
import ai.agentican.framework.llm.LlmRequest;
import ai.agentican.framework.llm.LlmResponse;
import ai.agentican.framework.llm.StopReason;
import ai.agentican.framework.llm.ToolCall;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

public class MockLlmClient {

    private final CopyOnWriteArrayList<MockEntry> entries = new CopyOnWriteArrayList<>();

    public MockLlmClient onSend(String matchSubstring, String responseText) {

        entries.add(new MockEntry(matchSubstring, endTurn(responseText)));
        return this;
    }

    public MockLlmClient onSend(String matchSubstring, LlmResponse response) {

        entries.add(new MockEntry(matchSubstring, response));
        return this;
    }

    public LlmClient toLlmClient() {

        return (LlmClient) this::send;
    }

    private synchronized LlmResponse send(LlmRequest request) {

        var fullText = (request.systemPrompt() != null ? request.systemPrompt() : "")
                + " " + request.userMessage();

        for (int i = 0; i < entries.size(); i++) {

            if (fullText.contains(entries.get(i).match)) {

                var response = entries.get(i).response;
                entries.remove(i);
                return response;
            }
        }

        throw new IllegalStateException("No mock response found.\n  Remaining entries: "
                + entries.stream().map(e -> e.match).toList()
                + "\n  Request preview: " + fullText.substring(0, Math.min(200, fullText.length())));
    }

    public static LlmResponse endTurn(String text) {

        return new LlmResponse(text, List.of(), StopReason.END_TURN, 0, 0, 0, 0, 0);
    }

    public static LlmResponse toolUse(String text, String toolName, Map<String, Object> args) {

        return new LlmResponse(text, List.of(new ToolCall("mock-" + toolName, toolName, args)),
                StopReason.TOOL_USE, 0, 0, 0, 0, 0);
    }

    public static String readResource(String path) {

        try (var is = MockLlmClient.class.getClassLoader().getResourceAsStream(path)) {

            if (is == null)
                throw new IllegalStateException("Test resource not found: " + path);

            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
        catch (IOException e) {

            throw new IllegalStateException("Failed to read test resource: " + path, e);
        }
    }

    private record MockEntry(String match, LlmResponse response) {}
}
