package ai.agentican.framework.orchestration.execution;

import ai.agentican.framework.llm.TokenUsage;

import java.util.List;

public record TaskResult(
        String name,
        TaskStatus status,
        List<TaskStepResult> stepResults) {

    public static TaskResult of(String name, TaskStatus status, List<TaskStepResult> stepResults) {

        return new TaskResult(name, status, stepResults);
    }

    public TaskResult {

        if (name == null || name.isBlank())
            throw new IllegalArgumentException("Plan name is required");

        if (status == null)
            throw new IllegalArgumentException("Plan status is required");

        if (stepResults == null)
            stepResults = List.of();
    }

    public TokenUsage tokenUsage() {
        return TokenUsage.sum(stepResults.stream().map(TaskStepResult::tokenUsage));
    }

    public long inputTokens() { return tokenUsage().input(); }
    public long outputTokens() { return tokenUsage().output(); }
    public long cacheReadTokens() { return tokenUsage().cacheRead(); }
    public long cacheWriteTokens() { return tokenUsage().cacheWrite(); }
    public long webSearchRequests() { return tokenUsage().webSearches(); }

    public String output() {

        if (stepResults.isEmpty())
            return "";

        return stepResults.getLast().output();
    }
}
