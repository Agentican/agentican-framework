package ai.agentican.quarkus.rest.dto;

import ai.agentican.framework.state.StepLog;

import java.util.List;

public record StepLogView(
        String id,
        String stepName,
        String status,
        String output,
        int runCount,
        long inputTokens,
        long outputTokens,
        long cacheReadTokens,
        long cacheWriteTokens,
        String pendingCheckpointId,
        List<RunLogView> runs) {

    public static StepLogView of(StepLog log) {

        var runs = log.runs().stream()
                .map(RunLogView::of)
                .toList();

        return new StepLogView(
                log.id(),
                log.stepName(),
                log.status() != null ? log.status().name() : null,
                log.output(),
                log.runCount(),
                log.inputTokens(),
                log.outputTokens(),
                log.cacheReadTokens(),
                log.cacheWriteTokens(),
                log.checkpoint() != null ? log.checkpoint().id() : null,
                runs);
    }
}
