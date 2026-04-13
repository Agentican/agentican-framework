package ai.agentican.framework.state;

import ai.agentican.framework.llm.TokenUsage;
import ai.agentican.framework.orchestration.model.Plan;
import ai.agentican.framework.orchestration.execution.TaskStatus;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class TaskLog {

    private final String taskId;
    private final String taskName;
    private final Instant createdAt;

    private final Plan plan;

    private final Map<String, String> params;
    private final Map<String, StepLog> steps = new ConcurrentHashMap<>();

    private volatile TaskStatus status;
    private volatile Instant completedAt;

    public TaskLog(String taskId, String taskName, Plan plan, Map<String, String> params) {

        this.taskId = taskId;
        this.taskName = taskName;
        this.createdAt = Instant.now();
        this.plan = plan;
        this.params = Map.copyOf(params);
    }

    public String taskId() { return taskId; }
    public String taskName() { return taskName; }
    public Instant createdAt() { return createdAt; }
    public Instant completedAt() { return completedAt; }

    public Plan plan() { return plan; }
    public TaskStatus status() { return status; }

    public TokenUsage tokenUsage() {
        return TokenUsage.sum(steps.values().stream().map(StepLog::tokenUsage));
    }

    public long inputTokens() { return tokenUsage().input(); }
    public long outputTokens() { return tokenUsage().output(); }
    public long cacheReadTokens() { return tokenUsage().cacheRead(); }
    public long cacheWriteTokens() { return tokenUsage().cacheWrite(); }
    public long webSearchRequests() { return tokenUsage().webSearches(); }

    public Map<String, String> params() { return params; }
    public Map<String, StepLog> steps() { return Map.copyOf(steps); }

    public StepLog step(String stepName) { return steps.get(stepName); }

    public void addStep(String stepName, StepLog stepLog) { steps.put(stepName, stepLog); }

    public StepLog findStepById(String stepId) {

        for (var entry : steps.values())
            if (stepId.equals(entry.id())) return entry;

        return null;
    }

    public RunLog findRunById(String runId) {

        for (var step : steps.values())
            for (var run : step.runs())
                if (runId.equals(run.id())) return run;

        return null;
    }

    public TurnLog findTurnById(String turnId) {

        for (var step : steps.values())
            for (var run : step.runs())
                for (var turn : run.turns())
                    if (turnId.equals(turn.id())) return turn;

        return null;
    }

    public List<StepLog> completedSteps() {

        return steps.values().stream()
                .filter(s -> s.status() == TaskStatus.COMPLETED)
                .toList();
    }

    public void setStatus(TaskStatus status) { this.status = status; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
}
