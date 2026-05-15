package ai.agentican.framework.state;

import ai.agentican.framework.hitl.HitlCheckpoint;
import ai.agentican.framework.hitl.HitlResponse;
import ai.agentican.framework.llm.TokenUsage;
import ai.agentican.framework.orchestration.execution.WorkflowRunStatus;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class StepLog {

    private final String id;
    private final String stepName;
    private final Instant createdAt;

    private final List<RunLog> runs = new CopyOnWriteArrayList<>();

    private volatile String output;

    private volatile WorkflowRunStatus status;
    private volatile HitlCheckpoint checkpoint;
    private volatile Instant completedAt;
    private volatile TokenUsage aggregateTokenUsage;
    private volatile String branchChosenPath;
    private volatile HitlResponse hitlResponse;

    public StepLog(String id, String stepName) {

        this(id, stepName, Instant.now());
    }

    public StepLog(String id, String stepName, Instant createdAt) {

        this.id = id;
        this.stepName = stepName;
        this.createdAt = createdAt != null ? createdAt : Instant.now();
    }

    @JsonCreator
    public StepLog(@JsonProperty("id") String id,
                   @JsonProperty("stepName") String stepName,
                   @JsonProperty("createdAt") Instant createdAt,
                   @JsonProperty("completedAt") Instant completedAt,
                   @JsonProperty("output") String output,
                   @JsonProperty("status") WorkflowRunStatus status,
                   @JsonProperty("checkpoint") HitlCheckpoint checkpoint,
                   @JsonProperty("aggregateTokenUsage") TokenUsage aggregateTokenUsage,
                   @JsonProperty("branchChosenPath") String branchChosenPath,
                   @JsonProperty("hitlResponse") HitlResponse hitlResponse,
                   @JsonProperty("runs") List<RunLog> runs) {

        this(id, stepName, createdAt);

        if (output != null)              setOutput(output);
        if (status != null)              setStatus(status);
        if (checkpoint != null)          setCheckpoint(checkpoint);
        if (completedAt != null)         setCompletedAt(completedAt);
        if (aggregateTokenUsage != null) setAggregateTokenUsage(aggregateTokenUsage);
        if (branchChosenPath != null)    setBranchChosenPath(branchChosenPath);
        if (hitlResponse != null)        setHitlResponse(hitlResponse);

        if (runs != null) for (var r : runs) addRun(r);
    }

    public String id() { return id; }
    public String stepName() { return stepName; }
    public Instant createdAt() { return createdAt; }
    public Instant completedAt() { return completedAt; }
    public String output() { return output; }

    public WorkflowRunStatus status() { return status; }
    public HitlCheckpoint checkpoint() { return checkpoint; }

    public TokenUsage tokenUsage() {
        if (aggregateTokenUsage != null) return aggregateTokenUsage;
        return TokenUsage.sum(runs.stream().map(RunLog::tokenUsage));
    }

    public long inputTokens() { return tokenUsage().input(); }
    public long outputTokens() { return tokenUsage().output(); }
    public long cacheReadTokens() { return tokenUsage().cacheRead(); }
    public long cacheWriteTokens() { return tokenUsage().cacheWrite(); }
    public long webSearchRequests() { return tokenUsage().webSearches(); }

    public List<RunLog> runs() { return List.copyOf(runs); }

    public RunLog lastRun() { return runs.isEmpty() ? null : runs.getLast(); }

    public int runCount() { return runs.size(); }

    public void setOutput(String output) { this.output = output; }
    public void setStatus(WorkflowRunStatus status) { this.status = status; }
    public void setCheckpoint(HitlCheckpoint checkpoint) { this.checkpoint = checkpoint; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }

    public void addRun(RunLog run) { runs.add(run); }

    public void setAggregateTokenUsage(TokenUsage usage) { this.aggregateTokenUsage = usage; }

    public String branchChosenPath() { return branchChosenPath; }
    public void setBranchChosenPath(String pathName) { this.branchChosenPath = pathName; }

    public HitlResponse hitlResponse() { return hitlResponse; }
    public void setHitlResponse(HitlResponse response) { this.hitlResponse = response; }
}
