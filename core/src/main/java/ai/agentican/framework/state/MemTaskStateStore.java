package ai.agentican.framework.state;

import ai.agentican.framework.hitl.HitlCheckpoint;
import ai.agentican.framework.hitl.HitlResponse;
import ai.agentican.framework.llm.LlmRequest;
import ai.agentican.framework.llm.LlmResponse;
import ai.agentican.framework.llm.ToolCall;
import ai.agentican.framework.orchestration.model.Plan;
import ai.agentican.framework.orchestration.execution.TaskStatus;
import ai.agentican.framework.tools.ToolResult;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class MemTaskStateStore implements TaskStateStore {

    public static final int DEFAULT_MAX_SIZE = 1000;

    private final Map<String, TaskLog> logs;

    public MemTaskStateStore() {

        this(DEFAULT_MAX_SIZE);
    }

    public MemTaskStateStore(int maxSize) {

        this.logs = Collections.synchronizedMap(new LinkedHashMap<>(16, 0.75f, true) {

            @Override
            protected boolean removeEldestEntry(Map.Entry<String, TaskLog> eldest) {

                return size() > maxSize;
            }
        });
    }

    @Override
    public void taskStarted(String taskId, String taskName, Plan plan, Map<String, String> params) {

        logs.put(taskId, new TaskLog(taskId, taskName, plan, params));
    }

    @Override
    public void taskStarted(String taskId, String taskName, Plan plan, Map<String, String> params,
                            String parentTaskId, String parentStepId, int iterationIndex) {

        logs.put(taskId, new TaskLog(taskId, taskName, plan, params,
                parentTaskId, parentStepId, iterationIndex));
    }

    @Override
    public void taskCompleted(String taskId, TaskStatus status) {

        var log = requireLog(taskId);

        log.setStatus(status);
        log.setCompletedAt(java.time.Instant.now());
    }

    @Override
    public void stepStarted(String taskId, String stepId, String stepName) {

        var taskLog = requireLog(taskId);
        var stepLog = new StepLog(stepId, stepName);

        taskLog.addStep(stepName, stepLog);
    }

    @Override
    public void stepCompleted(String taskId, String stepId, TaskStatus status, String output) {

        var stepLog = requireLog(taskId).findStepById(stepId);

        if (stepLog != null) {

            stepLog.setStatus(status);
            stepLog.setOutput(output);
            stepLog.setCompletedAt(java.time.Instant.now());
        }
    }

    @Override
    public void stepTokenUsageAggregated(String taskId, String stepId, ai.agentican.framework.llm.TokenUsage usage) {

        var stepLog = requireLog(taskId).findStepById(stepId);

        if (stepLog != null && usage != null)
            stepLog.setAggregateTokenUsage(usage);
    }

    @Override
    public void runStarted(String taskId, String stepId, String runId, String agentName) {

        var stepLog = requireLog(taskId).findStepById(stepId);

        if (stepLog != null)
            stepLog.addRun(new RunLog(runId, stepLog.runCount(), agentName));
    }

    @Override
    public void runCompleted(String taskId, String runId) {

    }

    @Override
    public void turnStarted(String taskId, String runId, String turnId) {

        var runLog = requireLog(taskId).findRunById(runId);

        if (runLog != null)
            runLog.addTurn(new TurnLog(turnId, runLog.turns().size()));
    }

    @Override
    public void turnCompleted(String taskId, String turnId) {

        var turnLog = requireLog(taskId).findTurnById(turnId);

        if (turnLog != null)
            turnLog.complete();
    }

    @Override
    public void turnAbandoned(String taskId, String turnId) {

        var turnLog = requireLog(taskId).findTurnById(turnId);

        if (turnLog != null)
            turnLog.abandon();
    }

    @Override
    public void messageSent(String taskId, String turnId, LlmRequest request) {

        var turnLog = requireLog(taskId).findTurnById(turnId);

        if (turnLog != null)
            turnLog.setRequest(request);
    }

    @Override
    public void responseReceived(String taskId, String turnId, LlmResponse response) {

        var turnLog = requireLog(taskId).findTurnById(turnId);

        if (turnLog != null)
            turnLog.setResponse(response);
    }

    @Override
    public void toolCallStarted(String taskId, String turnId, ToolCall toolCall) {

    }

    @Override
    public void toolCallCompleted(String taskId, String turnId, ToolResult toolResult) {

        var turnLog = requireLog(taskId).findTurnById(turnId);

        if (turnLog != null)
            turnLog.addToolResult(toolResult);
    }

    @Override
    public void hitlNotified(String taskId, String stepId, HitlCheckpoint checkpoint) {

        var stepLog = requireLog(taskId).findStepById(stepId);

        if (stepLog != null)
            stepLog.setCheckpoint(checkpoint);
    }

    @Override
    public void hitlResponded(String taskId, String stepId, HitlResponse response) {

        var stepLog = requireLog(taskId).findStepById(stepId);

        if (stepLog != null && response != null)
            stepLog.setHitlResponse(response);
    }

    @Override
    public void branchPathChosen(String taskId, String stepId, String pathName) {

        var stepLog = requireLog(taskId).findStepById(stepId);

        if (stepLog != null)
            stepLog.setBranchChosenPath(pathName);
    }

    @Override
    public TaskLog load(String taskId) {

        return logs.get(taskId);
    }

    @Override
    public List<TaskLog> list() {

        synchronized (logs) {

            return List.copyOf(logs.values());
        }
    }

    private TaskLog requireLog(String taskId) {

        var log = logs.get(taskId);

        if (log == null)
            throw new IllegalStateException("No task log found for taskId: " + taskId);

        return log;
    }
}
