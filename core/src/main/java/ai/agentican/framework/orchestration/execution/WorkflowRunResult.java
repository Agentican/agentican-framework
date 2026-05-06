package ai.agentican.framework.orchestration.execution;

import ai.agentican.framework.llm.TokenUsage;

import java.util.List;

public record WorkflowRunResult(
        String name,
        WorkflowRunStatus status,
        List<WorkflowStepResult> stepResults) {

    public WorkflowRunResult {

        if (name == null || name.isBlank())
            throw new IllegalArgumentException("WorkflowDefinition name is required");

        if (status == null)
            throw new IllegalArgumentException("WorkflowDefinition status is required");

        if (stepResults == null)
            stepResults = List.of();
    }

    public TokenUsage tokenUsage() {
        return TokenUsage.sum(stepResults.stream().map(WorkflowStepResult::tokenUsage));
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
