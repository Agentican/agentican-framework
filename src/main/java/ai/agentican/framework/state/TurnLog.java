package ai.agentican.framework.state;

import ai.agentican.framework.llm.LlmRequest;
import ai.agentican.framework.llm.LlmResponse;
import ai.agentican.framework.tools.ToolResult;
import ai.agentican.framework.util.Ids;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class TurnLog {

    private final String id;
    private final int index;
    private final Instant startedAt;

    private final List<ToolResult> toolResults;

    private volatile String messageId;
    private volatile LlmRequest request;
    private volatile String responseId;
    private volatile LlmResponse response;
    private volatile Instant completedAt;

    public TurnLog(String id, int index) {

        this.id = id;
        this.index = index;
        this.startedAt = Instant.now();
        this.toolResults = new CopyOnWriteArrayList<>();
    }

    public TurnLog(int index, LlmRequest request, LlmResponse response, List<ToolResult> toolResults,
                   Instant startedAt, Instant completedAt) {

        this.id = Ids.generate();
        this.index = index;
        this.request = request;
        this.response = response;
        this.toolResults = new CopyOnWriteArrayList<>(toolResults != null ? toolResults : List.of());
        this.startedAt = startedAt != null ? startedAt : Instant.now();
        this.completedAt = completedAt;
    }

    public TurnLog(String id, int index, String messageId, LlmRequest request,
                   String responseId, LlmResponse response, List<ToolResult> toolResults,
                   Instant startedAt, Instant completedAt) {

        this.id = id;
        this.index = index;
        this.messageId = messageId;
        this.request = request;
        this.responseId = responseId;
        this.response = response;
        this.toolResults = new CopyOnWriteArrayList<>(toolResults != null ? toolResults : List.of());
        this.startedAt = startedAt != null ? startedAt : Instant.now();
        this.completedAt = completedAt;
    }

    public void setRequest(LlmRequest request) {
        this.messageId = Ids.generate();
        this.request = request;
    }

    public void setResponse(LlmResponse response) {
        this.responseId = Ids.generate();
        this.response = response;
    }

    public void addToolResult(ToolResult result) { toolResults.add(result); }

    public void complete() { this.completedAt = Instant.now(); }

    public String id() { return id; }

    public int index() { return index; }

    public String messageId() { return messageId; }
    public LlmRequest request() { return request; }

    public String responseId() { return responseId; }
    public LlmResponse response() { return response; }

    public List<ToolResult> toolResults() { return List.copyOf(toolResults); }

    public Instant startedAt() { return startedAt; }

    public Instant completedAt() { return completedAt; }
}
