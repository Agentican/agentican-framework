package ai.agentican.framework.state;

import ai.agentican.framework.llm.TokenUsage;
import ai.agentican.framework.orchestration.model.WorkflowDefinition;
import ai.agentican.framework.orchestration.execution.WorkflowRunStatus;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class WorkflowRunLog {

    private final String taskId;
    private final String taskName;
    private final Instant createdAt;

    private final WorkflowDefinition plan;

    private final Map<String, String> params;
    private final Map<String, StepLog> steps = new ConcurrentHashMap<>();

    private final String parentTaskId;
    private final String parentStepId;
    private final int iterationIndex;

    private volatile WorkflowRunStatus status;
    private volatile Instant completedAt;
    private volatile boolean planSnapshotCorrupt;

    public WorkflowRunLog(String taskId, String taskName, WorkflowDefinition plan, Map<String, String> params) {

        this(taskId, taskName, plan, params, null, null, 0);
    }

    public WorkflowRunLog(String taskId, String taskName, WorkflowDefinition plan, Map<String, String> params,
                   String parentTaskId, String parentStepId, int iterationIndex) {

        this(taskId, taskName, plan, params, parentTaskId, parentStepId, iterationIndex, Instant.now());
    }

    public WorkflowRunLog(String taskId, String taskName, WorkflowDefinition plan, Map<String, String> params,
                   String parentTaskId, String parentStepId, int iterationIndex, Instant createdAt) {

        this.taskId = taskId;
        this.taskName = taskName;
        this.createdAt = createdAt != null ? createdAt : Instant.now();
        this.plan = plan;
        this.params = Map.copyOf(params);
        this.parentTaskId = parentTaskId;
        this.parentStepId = parentStepId;
        this.iterationIndex = iterationIndex;
    }

    public String taskId() { return taskId; }
    public String taskName() { return taskName; }
    public Instant createdAt() { return createdAt; }
    public Instant completedAt() { return completedAt; }

    public WorkflowDefinition plan() { return plan; }
    public WorkflowRunStatus status() { return status; }

    public String parentTaskId() { return parentTaskId; }
    public String parentStepId() { return parentStepId; }
    public int iterationIndex() { return iterationIndex; }

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
                .filter(s -> s.status() == WorkflowRunStatus.COMPLETED)
                .toList();
    }

    public void setStatus(WorkflowRunStatus status) { this.status = status; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }

    public boolean planSnapshotCorrupt() { return planSnapshotCorrupt; }
    public void setPlanSnapshotCorrupt(boolean corrupt) { this.planSnapshotCorrupt = corrupt; }
}
