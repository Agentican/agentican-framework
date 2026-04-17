package ai.agentican.quarkus.rest.dto;

import ai.agentican.framework.state.TaskLog;

import java.time.Instant;

public record TaskSummary(
        String taskId,
        String taskName,
        String status,
        Instant createdAt,
        long inputTokens,
        long outputTokens,
        long cacheReadTokens,
        long cacheWriteTokens) {

    public static TaskSummary of(TaskLog log) {

        return new TaskSummary(
                log.taskId(),
                log.taskName(),
                log.status() != null ? log.status().name() : "RUNNING",
                log.createdAt(),
                log.inputTokens(),
                log.outputTokens(),
                log.cacheReadTokens(),
                log.cacheWriteTokens());
    }
}
