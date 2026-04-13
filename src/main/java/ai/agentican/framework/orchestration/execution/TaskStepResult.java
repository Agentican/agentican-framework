package ai.agentican.framework.orchestration.execution;

import ai.agentican.framework.agent.AgentResult;
import ai.agentican.framework.llm.TokenUsage;

import java.util.List;

/**
 * Result of executing a single task step. When a step fails with an exception,
 * {@code cause} carries the original throwable for observability.
 *
 * @param stepName      the step that was executed
 * @param status        terminal status
 * @param output        step output text
 * @param agentResults  per-agent-run results
 * @param cause         the original exception on failure, or {@code null} on success
 */
public record TaskStepResult(
        String stepName,
        TaskStatus status,
        String output,
        List<AgentResult> agentResults,
        Throwable cause) {

    public TaskStepResult {

        if (stepName == null || stepName.isBlank())
            throw new IllegalArgumentException("Step name is required");

        if (status == null)
            throw new IllegalArgumentException("Step status is required");

        if (agentResults == null)
            agentResults = List.of();
    }

    public TaskStepResult(String stepName, TaskStatus status, String output, List<AgentResult> agentResults) {

        this(stepName, status, output, agentResults, null);
    }

    public TokenUsage tokenUsage() {
        return TokenUsage.sum(agentResults.stream().map(AgentResult::tokenUsage));
    }

    public long inputTokens() { return tokenUsage().input(); }
    public long outputTokens() { return tokenUsage().output(); }
    public long cacheReadTokens() { return tokenUsage().cacheRead(); }
    public long cacheWriteTokens() { return tokenUsage().cacheWrite(); }
    public long webSearchRequests() { return tokenUsage().webSearches(); }
}
