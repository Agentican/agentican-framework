package ai.agentican.temporal.activity;

import ai.agentican.framework.hitl.HitlCheckpoint;
import ai.agentican.framework.hitl.HitlResponse;
import ai.agentican.framework.llm.LlmRequest;
import ai.agentican.framework.llm.LlmResponse;
import ai.agentican.framework.llm.TokenUsage;
import ai.agentican.framework.llm.ToolCall;
import ai.agentican.framework.orchestration.execution.WorkflowRunStatus;
import ai.agentican.framework.state.WorkflowRunLog;
import ai.agentican.framework.tools.ToolResult;
import ai.agentican.temporal.dto.TaskStartedRequest;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

import java.util.List;

@ActivityInterface
public interface WorkflowRunStoreActivity {

    @ActivityMethod
    void taskStarted(TaskStartedRequest request);

    @ActivityMethod
    void taskCompleted(String taskId, WorkflowRunStatus status);

    @ActivityMethod
    void stepStarted(String taskId, String stepId, String stepName);

    @ActivityMethod
    void stepCompleted(String taskId, String stepId, WorkflowRunStatus status, String output);

    @ActivityMethod
    void stepTokenUsageAggregated(String taskId, String stepId, TokenUsage usage);

    @ActivityMethod
    void runStarted(String taskId, String stepId, String runId, String agentName);

    @ActivityMethod
    void runCompleted(String taskId, String runId);

    @ActivityMethod
    void turnStarted(String taskId, String runId, String turnId);

    @ActivityMethod
    void turnCompleted(String taskId, String turnId);

    @ActivityMethod
    void turnAbandoned(String taskId, String turnId);

    @ActivityMethod
    void messageSent(String taskId, String turnId, LlmRequest request);

    @ActivityMethod
    void responseReceived(String taskId, String turnId, LlmResponse response);

    @ActivityMethod
    void toolCallStarted(String taskId, String turnId, ToolCall toolCall);

    @ActivityMethod
    void toolCallCompleted(String taskId, String turnId, ToolResult toolResult);

    @ActivityMethod
    void hitlNotified(String taskId, String stepId, HitlCheckpoint checkpoint);

    @ActivityMethod
    void hitlResponded(String taskId, String stepId, HitlResponse response);

    @ActivityMethod
    void branchPathChosen(String taskId, String stepId, String pathName);

    @ActivityMethod
    WorkflowRunLog load(String taskId);

    @ActivityMethod
    List<WorkflowRunLog> list();

    @ActivityMethod
    List<WorkflowRunLog> listInProgress();
}
