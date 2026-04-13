package ai.agentican.framework.state;

import ai.agentican.framework.hitl.HitlCheckpoint;
import ai.agentican.framework.hitl.HitlResponse;
import ai.agentican.framework.llm.LlmRequest;
import ai.agentican.framework.llm.LlmResponse;
import ai.agentican.framework.llm.ToolCall;
import ai.agentican.framework.orchestration.model.Plan;
import ai.agentican.framework.orchestration.execution.TaskStatus;
import ai.agentican.framework.tools.ToolResult;

import java.util.List;
import java.util.Map;

public interface TaskStateStore {

    void taskStarted(String taskId, String taskName, Plan plan, Map<String, String> params);

    void taskCompleted(String taskId, TaskStatus status);

    void stepStarted(String taskId, String stepId, String stepName);

    void stepCompleted(String taskId, String stepId, TaskStatus status, String output);

    void runStarted(String taskId, String stepId, String runId, String agentName);

    void runCompleted(String taskId, String runId);

    void turnStarted(String taskId, String runId, String turnId);

    void turnCompleted(String taskId, String turnId);

    void messageSent(String taskId, String turnId, LlmRequest request);

    void responseReceived(String taskId, String turnId, LlmResponse response);

    void toolCallStarted(String taskId, String turnId, ToolCall toolCall);

    void toolCallCompleted(String taskId, String turnId, ToolResult toolResult);

    void hitlNotified(String taskId, String stepId, HitlCheckpoint checkpoint);

    void hitlResponded(String taskId, String stepId, HitlResponse response);

    TaskLog load(String taskId);

    List<TaskLog> list();
}
