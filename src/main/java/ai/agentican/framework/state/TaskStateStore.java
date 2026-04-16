package ai.agentican.framework.state;

import ai.agentican.framework.hitl.HitlCheckpoint;
import ai.agentican.framework.hitl.HitlResponse;
import ai.agentican.framework.llm.LlmRequest;
import ai.agentican.framework.llm.LlmResponse;
import ai.agentican.framework.llm.TokenUsage;
import ai.agentican.framework.llm.ToolCall;
import ai.agentican.framework.orchestration.model.Plan;
import ai.agentican.framework.orchestration.execution.TaskStatus;
import ai.agentican.framework.tools.ToolResult;

import java.util.List;
import java.util.Map;

public interface TaskStateStore {

    void taskStarted(String taskId, String taskName, Plan plan, Map<String, String> params);

    void taskStarted(String taskId, String taskName, Plan plan, Map<String, String> params,
                     String parentTaskId, String parentStepId, int iterationIndex);

    void taskCompleted(String taskId, TaskStatus status);

    void stepStarted(String taskId, String stepId, String stepName);

    void stepCompleted(String taskId, String stepId, TaskStatus status, String output);

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

    TaskLog load(String taskId);

    List<TaskLog> list();

    default List<TaskLog> listInProgress() {

        return list().stream().filter(t -> t.status() == null).toList();
    }
}
