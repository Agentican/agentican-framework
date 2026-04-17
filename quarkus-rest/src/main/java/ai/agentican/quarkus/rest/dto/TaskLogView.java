package ai.agentican.quarkus.rest.dto;

import ai.agentican.framework.state.TaskLog;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record TaskLogView(
        String taskId,
        String taskName,
        String planId,
        String status,
        Instant createdAt,
        Instant completedAt,
        Long durationMs,
        Map<String, String> params,
        List<StepLogView> steps,
        long inputTokens,
        long outputTokens,
        long cacheReadTokens,
        long cacheWriteTokens) {

    public static TaskLogView of(TaskLog log) {

        var steps = log.steps().values().stream()
                .map(StepLogView::of)
                .toList();

        var durationMs = log.createdAt() != null && log.completedAt() != null
                ? java.time.Duration.between(log.createdAt(), log.completedAt()).toMillis()
                : null;

        return new TaskLogView(
                log.taskId(),
                log.taskName(),
                log.plan() != null ? log.plan().id() : null,
                log.status() != null ? log.status().name() : "RUNNING",
                log.createdAt(),
                log.completedAt(),
                durationMs,
                log.params(),
                steps,
                log.inputTokens(),
                log.outputTokens(),
                log.cacheReadTokens(),
                log.cacheWriteTokens());
    }
}
