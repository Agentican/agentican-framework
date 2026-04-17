package ai.agentican.quarkus.rest.dto;

import ai.agentican.framework.state.RunLog;

import java.time.Instant;
import java.util.List;

public record RunLogView(
        String id,
        int index,
        String agentName,
        Instant startedAt,
        Instant completedAt,
        List<TurnLogView> turns,
        long inputTokens,
        long outputTokens,
        long cacheReadTokens,
        long cacheWriteTokens,
        long webSearchRequests) {

    public static RunLogView of(RunLog run) {

        var turns = run.turns().stream().map(TurnLogView::of).toList();

        return new RunLogView(
                run.id(),
                run.index(),
                run.agentName(),
                run.startedAt(),
                run.completedAt(),
                turns,
                run.inputTokens(),
                run.outputTokens(),
                run.cacheReadTokens(),
                run.cacheWriteTokens(),
                run.webSearchRequests());
    }
}
