package ai.agentican.framework.agent;

import ai.agentican.framework.hitl.HitlCheckpoint;
import ai.agentican.framework.llm.TokenUsage;
import ai.agentican.framework.state.RunLog;
import ai.agentican.framework.util.Ids;

public record AgentResult(
        AgentStatus status,
        RunLog run,
        HitlCheckpoint checkpoint) {

    public AgentResult {

        if (status == null) throw new IllegalArgumentException("Status is required");
        if (run == null) run = new RunLog(Ids.generate(), 0, null);
    }

    public AgentResult(AgentStatus status, RunLog run) {

        this(status, run, null);
    }

    public String text() {

        if (run.turns().isEmpty())
            return status.defaultText();

        return run.turns().getLast().response().text();
    }

    public boolean isCompleted() { return status == AgentStatus.COMPLETED; }
    public boolean isSuspended() { return status == AgentStatus.SUSPENDED; }

    public TokenUsage tokenUsage() { return run.tokenUsage(); }

    public long inputTokens() { return tokenUsage().input(); }
    public long outputTokens() { return tokenUsage().output(); }
    public long cacheReadTokens() { return tokenUsage().cacheRead(); }
    public long cacheWriteTokens() { return tokenUsage().cacheWrite(); }
    public long webSearchRequests() { return tokenUsage().webSearches(); }
}
