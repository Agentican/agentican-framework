package ai.agentican.framework.agent;

import ai.agentican.framework.hitl.HitlCheckpoint;
import ai.agentican.framework.hitl.HitlResponse;
import ai.agentican.framework.knowledge.KnowledgeEntry;
import ai.agentican.framework.llm.LlmRequest;
import ai.agentican.framework.llm.LlmResponse;
import ai.agentican.framework.llm.TokenUsage;
import ai.agentican.framework.llm.ToolCall;
import ai.agentican.framework.orchestration.model.WorkflowDefinition;
import ai.agentican.framework.state.WorkflowRunLog;
import ai.agentican.framework.tools.ToolResult;
import ai.agentican.framework.tools.Toolkit;

import java.time.Instant;
import java.util.Map;

public interface AgentLoopHost {

    LlmResponse callLlm(LlmRequest request);

    String executeTool(String toolName, Map<String, Object> args, Toolkit toolkit);

    Instant now();

    String newId();

    WorkflowRunLog loadRunLog(String taskId);

    void taskStarted(String taskId, String taskName, WorkflowDefinition plan, Map<String, String> params);

    void stepStarted(String taskId, String stepId, String stepName);

    void runStarted(String taskId, String stepId, String runId, String agentName);

    void runCompleted(String taskId, String stepId, String runId, AgentStatus status, TokenUsage tokenUsage);

    void turnStarted(String taskId, String runId, String turnId, int index);

    void turnCompleted(String taskId, String turnId, int index, TokenUsage tokenUsage);

    void turnAbandoned(String taskId, String turnId);

    void messageSent(String taskId, String turnId, LlmRequest request);

    void responseReceived(String taskId, String turnId, LlmResponse response);

    void toolCallStarted(String taskId, String turnId, ToolCall toolCall);

    void toolCallCompleted(String taskId, String turnId, ToolResult result);

    void hitlNotified(String taskId, String stepId, HitlCheckpoint checkpoint);

    HitlCheckpoint createToolApprovalCheckpoint(ToolCall call, String stepName);

    HitlCheckpoint createQuestionCheckpoint(String question, String context, String stepName);

    HitlResponse awaitHitlResponse(HitlCheckpoint checkpoint);

    KnowledgeEntry knowledgeEntry(String entryId);

    boolean isCancelled();

    default boolean isManaged() { return false; }
}
