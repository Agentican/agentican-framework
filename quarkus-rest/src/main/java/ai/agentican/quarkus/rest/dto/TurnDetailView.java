package ai.agentican.quarkus.rest.dto;

import ai.agentican.framework.state.TurnLog;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public record TurnDetailView(
        int index,
        Instant startedAt,
        Instant completedAt,

        String systemPrompt,
        String userMessage,
        String model,
        String provider,

        String responseText,
        String stopReason,
        long inputTokens,
        long outputTokens,
        long cacheReadTokens,
        long cacheWriteTokens,

        List<ToolCallView> toolCalls) {

    public record ToolCallView(
            String id,
            String toolName,
            Map<String, Object> args,
            String result,
            boolean error) {}

    public static TurnDetailView of(TurnLog turn) {

        var req = turn.request();
        var resp = turn.response();

        var resultsByCallId = turn.toolResults().stream()
                .collect(Collectors.toMap(r -> r.toolCallId(), r -> r, (a, b) -> a));

        var toolCalls = resp != null && resp.toolCalls() != null
                ? resp.toolCalls().stream().map(tc -> {
                    var result = resultsByCallId.get(tc.id());
                    return new ToolCallView(
                            tc.id(),
                            tc.toolName(),
                            tc.args(),
                            result != null ? result.content() : null,
                            result != null && result.isError());
                }).toList()
                : List.<ToolCallView>of();

        return new TurnDetailView(
                turn.index(),
                turn.startedAt(),
                turn.completedAt(),
                req != null ? req.systemPrompt() : null,
                req != null ? concatUserText(req.userTask(), req.userMessage()) : null,
                req != null ? req.model() : null,
                req != null ? req.provider() : null,
                resp != null ? resp.text() : null,
                resp != null ? resp.stopReason().name() : null,
                resp != null ? resp.inputTokens() : 0,
                resp != null ? resp.outputTokens() : 0,
                resp != null ? resp.cacheReadTokens() : 0,
                resp != null ? resp.cacheWriteTokens() : 0,
                toolCalls);
    }

    private static String concatUserText(String userTask, String userMessage) {

        var task = userTask != null ? userTask : "";
        var msg = userMessage != null ? userMessage : "";

        if (task.isBlank()) return msg;
        if (msg.isBlank()) return task;
        return task + "\n" + msg;
    }
}
