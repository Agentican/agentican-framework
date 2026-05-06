package ai.agentican.framework.orchestration.execution;

import ai.agentican.framework.agent.AgentResult;
import ai.agentican.framework.llm.TokenUsage;

import java.util.List;

public record WorkflowStepResult(
        String name,
        WorkflowRunStatus status,
        String output,
        List<AgentResult> agentResults,
        Throwable cause) {

    public WorkflowStepResult {

        if (name == null || name.isBlank())
            throw new IllegalArgumentException("Step name is required");

        if (status == null)
            throw new IllegalArgumentException("Step status is required");

        if (agentResults == null)
            agentResults = List.of();
    }

    public WorkflowStepResult(String name, WorkflowRunStatus status, String output, List<AgentResult> agentResults) {

        this(name, status, output, agentResults, null);
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
