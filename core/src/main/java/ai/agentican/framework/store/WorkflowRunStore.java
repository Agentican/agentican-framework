package ai.agentican.framework.store;

import ai.agentican.framework.hitl.HitlCheckpoint;
import ai.agentican.framework.hitl.HitlResponse;
import ai.agentican.framework.llm.LlmRequest;
import ai.agentican.framework.llm.LlmResponse;
import ai.agentican.framework.llm.TokenUsage;
import ai.agentican.framework.llm.ToolCall;
import ai.agentican.framework.orchestration.model.WorkflowDefinition;
import ai.agentican.framework.orchestration.execution.WorkflowRunStatus;
import ai.agentican.framework.state.RuntimeOwner;
import ai.agentican.framework.state.WorkflowRunLog;
import ai.agentican.framework.tools.ToolResult;

import java.util.List;
import java.util.Map;

public interface WorkflowRunStore {

    void taskStarted(String taskId, String taskName, WorkflowDefinition plan, Map<String, String> params,
                     RuntimeOwner runtime, String temporalWorkflowId);

    void taskStarted(String taskId, String taskName, WorkflowDefinition plan, Map<String, String> params,
                     String parentTaskId, String parentStepId, int iterationIndex,
                     RuntimeOwner runtime, String temporalWorkflowId);

    void taskCompleted(String taskId, WorkflowRunStatus status);

    void stepStarted(String taskId, String stepId, String stepName);

    void stepCompleted(String taskId, String stepId, WorkflowRunStatus status, String output);

    void stepTokenUsageAggregated(String taskId, String stepId, TokenUsage usage);

    void runStarted(String taskId, String stepId, String runId, String agentName);

    void runCompleted(String taskId, String runId);

    void turnStarted(String taskId, String runId, String turnId);

    void turnCompleted(String taskId, String turnId);

    default void turnAbandoned(String taskId, String turnId) { turnCompleted(taskId, turnId); }

    void messageSent(String taskId, String turnId, LlmRequest request);

    void responseReceived(String taskId, String turnId, LlmResponse response);

    void toolCallStarted(String taskId, String turnId, ToolCall toolCall);

    void toolCallCompleted(String taskId, String turnId, ToolResult toolResult);

    void hitlNotified(String taskId, String stepId, HitlCheckpoint checkpoint);

    void hitlResponded(String taskId, String stepId, HitlResponse response);

    default void branchPathChosen(String taskId, String stepId, String pathName) {}

    WorkflowRunLog load(String taskId);

    List<WorkflowRunLog> list();

    default List<WorkflowRunLog> listInProgress() {

        return list().stream().filter(t -> t.status() == null).toList();
    }
}
