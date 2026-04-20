package ai.agentican.framework.store;

import ai.agentican.framework.orchestration.execution.TaskListener;
import ai.agentican.framework.hitl.HitlCheckpoint;
import ai.agentican.framework.hitl.HitlResponse;
import ai.agentican.framework.llm.LlmRequest;
import ai.agentican.framework.llm.LlmResponse;
import ai.agentican.framework.llm.ToolCall;
import ai.agentican.framework.orchestration.model.Plan;
import ai.agentican.framework.orchestration.execution.TaskStatus;
import ai.agentican.framework.state.TaskLog;
import ai.agentican.framework.tools.ToolResult;

import java.util.List;
import java.util.Map;

public class TaskStateStoreNotifying implements TaskStateStore {

    private final TaskStateStore delegate;
    private final TaskListener listener;

    public TaskStateStoreNotifying(TaskStateStore delegate, TaskListener listener) {

        this.delegate = delegate;
        this.listener = listener != null ? listener : new TaskListener() {};
    }

    @Override
    public void taskStarted(String taskId, String taskName, Plan plan, Map<String, String> params) {

        delegate.taskStarted(taskId, taskName, plan, params);
        listener.onTaskStarted(taskId);
    }

    @Override
    public void taskStarted(String taskId, String taskName, Plan plan, Map<String, String> params,
                            String parentTaskId, String parentStepId, int iterationIndex) {

        delegate.taskStarted(taskId, taskName, plan, params, parentTaskId, parentStepId, iterationIndex);
        listener.onTaskStarted(taskId);
    }

    @Override
    public void taskCompleted(String taskId, TaskStatus status) {

        delegate.taskCompleted(taskId, status);
        listener.onTaskCompleted(taskId, status);
    }

    @Override
    public void stepStarted(String taskId, String stepId, String stepName) {

        delegate.stepStarted(taskId, stepId, stepName);
        listener.onStepStarted(taskId, stepId);
    }

    @Override
    public void stepCompleted(String taskId, String stepId, TaskStatus status, String output) {

        delegate.stepCompleted(taskId, stepId, status, output);
        listener.onStepCompleted(taskId, stepId);
    }

    @Override
    public void stepTokenUsageAggregated(String taskId, String stepId, ai.agentican.framework.llm.TokenUsage usage) {

        delegate.stepTokenUsageAggregated(taskId, stepId, usage);
    }

    @Override
    public void runStarted(String taskId, String stepId, String runId, String agentName) {

        delegate.runStarted(taskId, stepId, runId, agentName);
        listener.onRunStarted(taskId, runId);
    }

    @Override
    public void runCompleted(String taskId, String runId) {

        delegate.runCompleted(taskId, runId);
        listener.onRunCompleted(taskId, runId, ai.agentican.framework.agent.AgentStatus.COMPLETED);
    }

    @Override
    public void turnStarted(String taskId, String runId, String turnId) {

        delegate.turnStarted(taskId, runId, turnId);
        listener.onTurnStarted(taskId, turnId);
    }

    @Override
    public void turnCompleted(String taskId, String turnId) {

        delegate.turnCompleted(taskId, turnId);
        listener.onTurnCompleted(taskId, turnId);
    }

    @Override
    public void turnAbandoned(String taskId, String turnId) {

        delegate.turnAbandoned(taskId, turnId);
        listener.onTurnCompleted(taskId, turnId);
    }

    @Override
    public void messageSent(String taskId, String turnId, LlmRequest request) {

        delegate.messageSent(taskId, turnId, request);
        listener.onMessageSent(taskId, turnId);
    }

    @Override
    public void responseReceived(String taskId, String turnId, LlmResponse response) {

        delegate.responseReceived(taskId, turnId, response);
        listener.onResponseReceived(taskId, turnId, response.stopReason());
    }

    @Override
    public void toolCallStarted(String taskId, String turnId, ToolCall toolCall) {

        delegate.toolCallStarted(taskId, turnId, toolCall);
        listener.onToolCallStarted(taskId, toolCall.id());
    }

    @Override
    public void toolCallCompleted(String taskId, String turnId, ToolResult toolResult) {

        delegate.toolCallCompleted(taskId, turnId, toolResult);
        listener.onToolCallCompleted(taskId, toolResult.toolCallId());
    }

    @Override
    public void hitlNotified(String taskId, String stepId, HitlCheckpoint checkpoint) {

        delegate.hitlNotified(taskId, stepId, checkpoint);
        listener.onHitlNotified(taskId, checkpoint.id(), checkpoint.type());
    }

    @Override
    public void hitlResponded(String taskId, String stepId, HitlResponse response) {

        var taskLog = delegate.load(taskId);

        var stepLog = taskLog != null ? taskLog.findStepById(stepId) : null;
        var checkpoint = stepLog != null ? stepLog.checkpoint() : null;
        var hitlId = checkpoint != null ? checkpoint.id() : "unknown";

        delegate.hitlResponded(taskId, stepId, response);
        listener.onHitlResponded(taskId, hitlId, response.approved());
    }

    @Override
    public void branchPathChosen(String taskId, String stepId, String pathName) {

        delegate.branchPathChosen(taskId, stepId, pathName);
    }

    @Override
    public TaskLog load(String taskId) {

        return delegate.load(taskId);
    }

    @Override
    public List<TaskLog> list() {

        return delegate.list();
    }

    @Override
    public List<TaskLog> listInProgress() {

        return delegate.listInProgress();
    }
}
