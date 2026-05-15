package ai.agentican.framework.agent;

import ai.agentican.framework.hitl.HitlCheckpoint;
import ai.agentican.framework.hitl.HitlManager;
import ai.agentican.framework.hitl.HitlResponse;
import ai.agentican.framework.knowledge.KnowledgeEntry;
import ai.agentican.framework.llm.LlmClient;
import ai.agentican.framework.llm.LlmRequest;
import ai.agentican.framework.llm.LlmResponse;
import ai.agentican.framework.llm.ToolCall;
import ai.agentican.framework.orchestration.model.WorkflowDefinition;
import ai.agentican.framework.state.WorkflowRunLog;
import ai.agentican.framework.store.KnowledgeStore;
import ai.agentican.framework.store.WorkflowRunStore;
import ai.agentican.framework.tools.ToolResult;
import ai.agentican.framework.tools.Toolkit;
import ai.agentican.framework.util.Ids;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

public class InProcessAgentLoopHost implements AgentLoopHost {

    private final LlmClient llmClient;
    private final WorkflowRunStore workflowRunStore;
    private final HitlManager hitlManager;
    private final KnowledgeStore knowledgeStore;
    private final AtomicBoolean cancelled;

    public InProcessAgentLoopHost(LlmClient llmClient, WorkflowRunStore workflowRunStore,
                                  HitlManager hitlManager, KnowledgeStore knowledgeStore,
                                  AtomicBoolean cancelled) {

        this.llmClient        = Objects.requireNonNull(llmClient,        "llmClient");
        this.workflowRunStore = Objects.requireNonNull(workflowRunStore, "workflowRunStore");
        this.hitlManager      = hitlManager;       // null allowed — required only when HITL methods are invoked
        this.knowledgeStore   = knowledgeStore;    // null allowed — knowledgeEntry returns null when no store
        this.cancelled        = cancelled != null ? cancelled : new AtomicBoolean(false);
    }

    @Override
    public LlmResponse callLlm(LlmRequest request) {

        return llmClient.send(request);
    }

    @Override
    public String executeTool(String toolName, Map<String, Object> args, Toolkit toolkit) {

        try {

            return toolkit.execute(toolName, args);
        }
        catch (RuntimeException e) {

            throw e;
        }
        catch (Exception e) {

            throw new RuntimeException("Tool '" + toolName + "' threw: " + e.getMessage(), e);
        }
    }

    @Override public Instant now()    { return Instant.now(); }
    @Override public String  newId()  { return Ids.generate(); }

    @Override public WorkflowRunLog loadRunLog(String taskId) {
        return workflowRunStore.load(taskId);
    }

    @Override public void taskStarted(String taskId, String taskName, WorkflowDefinition plan,
                                      Map<String, String> params) {
        workflowRunStore.taskStarted(taskId, taskName, plan, params);
    }

    @Override public void stepStarted(String taskId, String stepId, String stepName) {
        workflowRunStore.stepStarted(taskId, stepId, stepName);
    }

    @Override public void runStarted(String taskId, String stepId, String runId, String agentName) {
        workflowRunStore.runStarted(taskId, stepId, runId, agentName);
    }

    @Override public void runCompleted(String taskId, String runId) {
        workflowRunStore.runCompleted(taskId, runId);
    }

    @Override public void turnStarted(String taskId, String runId, String turnId) {
        workflowRunStore.turnStarted(taskId, runId, turnId);
    }

    @Override public void turnCompleted(String taskId, String turnId) {
        workflowRunStore.turnCompleted(taskId, turnId);
    }

    @Override public void turnAbandoned(String taskId, String turnId) {
        workflowRunStore.turnAbandoned(taskId, turnId);
    }

    @Override public void messageSent(String taskId, String turnId, LlmRequest request) {
        workflowRunStore.messageSent(taskId, turnId, request);
    }

    @Override public void responseReceived(String taskId, String turnId, LlmResponse response) {
        workflowRunStore.responseReceived(taskId, turnId, response);
    }

    @Override public void toolCallStarted(String taskId, String turnId, ToolCall toolCall) {
        workflowRunStore.toolCallStarted(taskId, turnId, toolCall);
    }

    @Override public void toolCallCompleted(String taskId, String turnId, ToolResult result) {
        workflowRunStore.toolCallCompleted(taskId, turnId, result);
    }

    @Override public void hitlNotified(String taskId, String stepId, HitlCheckpoint checkpoint) {
        workflowRunStore.hitlNotified(taskId, stepId, checkpoint);
    }

    @Override public void hitlResponded(String taskId, String stepId, HitlResponse response) {
        workflowRunStore.hitlResponded(taskId, stepId, response);
    }

    @Override
    public HitlCheckpoint createToolApprovalCheckpoint(ToolCall call, String stepName) {

        requireHitlManager();

        return hitlManager.createToolApprovalCheckpoint(call, stepName);
    }

    @Override
    public HitlCheckpoint createQuestionCheckpoint(String question, String context, String stepName) {

        requireHitlManager();

        return hitlManager.createQuestionCheckpoint(question, context, stepName);
    }

    @Override
    public HitlResponse awaitHitlResponse(HitlCheckpoint checkpoint) {

        requireHitlManager();

        return hitlManager.awaitResponse(checkpoint.id());
    }

    private void requireHitlManager() {

        if (hitlManager == null)
            throw new IllegalStateException(
                    "HITL operation invoked but no HitlManager was supplied when constructing the host");
    }

    @Override
    public KnowledgeEntry knowledgeEntry(String entryId) {

        return knowledgeStore == null ? null : knowledgeStore.get(entryId);
    }

    @Override
    public boolean isCancelled() {

        return cancelled.get();
    }
}
