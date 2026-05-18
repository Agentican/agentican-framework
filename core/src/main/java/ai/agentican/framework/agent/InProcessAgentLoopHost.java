package ai.agentican.framework.agent;

import ai.agentican.framework.event.AgenticanEventBus;
import ai.agentican.framework.event.HitlNotified;
import ai.agentican.framework.event.MessageSent;
import ai.agentican.framework.event.ResponseReceived;
import ai.agentican.framework.event.RunCompleted;
import ai.agentican.framework.event.RunStarted;
import ai.agentican.framework.event.StepStarted;
import ai.agentican.framework.event.TaskStarted;
import ai.agentican.framework.event.ToolCallCompleted;
import ai.agentican.framework.event.ToolCallStarted;
import ai.agentican.framework.event.TurnAbandoned;
import ai.agentican.framework.event.TurnCompleted;
import ai.agentican.framework.event.TurnStarted;
import ai.agentican.framework.hitl.HitlCheckpoint;
import ai.agentican.framework.hitl.HitlManager;
import ai.agentican.framework.hitl.HitlResponse;
import ai.agentican.framework.knowledge.KnowledgeEntry;
import ai.agentican.framework.llm.LlmClient;
import ai.agentican.framework.llm.LlmRequest;
import ai.agentican.framework.llm.LlmResponse;
import ai.agentican.framework.llm.TokenUsage;
import ai.agentican.framework.llm.ToolCall;
import ai.agentican.framework.orchestration.model.WorkflowDefinition;
import ai.agentican.framework.state.RuntimeOwner;
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

/**
 * In-process implementation of {@link AgentLoopHost} for the SmacAgentRunner /
 * ReActAgentRunner — translates host-mediated operations into
 * {@link AgenticanEventBus#publish event-bus publishes}. The state store is
 * updated by a {@code WorkflowRunStorePersister} subscribed to the same bus,
 * so the same persistence happens, just via the event channel.
 *
 * <p>The {@code WorkflowRunStore} reference is kept only for the read path
 * ({@link #loadRunLog}) — the runner's own state introspection.
 */
public class InProcessAgentLoopHost implements AgentLoopHost {

    private final LlmClient llmClient;
    private final WorkflowRunStore workflowRunStore;
    private final AgenticanEventBus eventBus;
    private final HitlManager hitlManager;
    private final KnowledgeStore knowledgeStore;
    private final AtomicBoolean cancelled;

    public InProcessAgentLoopHost(LlmClient llmClient, WorkflowRunStore workflowRunStore,
                                  AgenticanEventBus eventBus, HitlManager hitlManager,
                                  KnowledgeStore knowledgeStore, AtomicBoolean cancelled) {

        this.llmClient        = Objects.requireNonNull(llmClient,        "llmClient");
        this.workflowRunStore = Objects.requireNonNull(workflowRunStore, "workflowRunStore");
        this.eventBus         = Objects.requireNonNull(eventBus,         "eventBus");
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
        eventBus.publish(new TaskStarted(taskId, taskName, plan, params, null, null, 0,
                RuntimeOwner.IN_PROCESS, null));
    }

    @Override public void stepStarted(String taskId, String stepId, String stepName) {
        eventBus.publish(new StepStarted(taskId, stepId, stepName));
    }

    @Override public void runStarted(String taskId, String stepId, String runId, String agentName) {
        eventBus.publish(new RunStarted(taskId, stepId, runId, agentName));
    }

    @Override public void runCompleted(String taskId, String stepId, String runId,
                                       AgentStatus status, TokenUsage tokenUsage) {
        eventBus.publish(new RunCompleted(taskId, stepId, runId,
                status != null ? status : AgentStatus.COMPLETED,
                tokenUsage != null ? tokenUsage : TokenUsage.ZERO));
    }

    @Override public void turnStarted(String taskId, String runId, String turnId, int index) {
        eventBus.publish(new TurnStarted(taskId, runId, turnId, index));
    }

    @Override public void turnCompleted(String taskId, String turnId, int index, TokenUsage tokenUsage) {
        eventBus.publish(new TurnCompleted(taskId, turnId, index, tokenUsage != null ? tokenUsage : TokenUsage.ZERO));
    }

    @Override public void turnAbandoned(String taskId, String turnId) {
        eventBus.publish(new TurnAbandoned(taskId, turnId));
    }

    @Override public void messageSent(String taskId, String turnId, LlmRequest request) {
        eventBus.publish(new MessageSent(taskId, turnId, request));
    }

    @Override public void responseReceived(String taskId, String turnId, LlmResponse response) {
        eventBus.publish(new ResponseReceived(taskId, turnId, response));
    }

    @Override public void toolCallStarted(String taskId, String turnId, ToolCall toolCall) {
        eventBus.publish(new ToolCallStarted(taskId, turnId, toolCall));
    }

    @Override public void toolCallCompleted(String taskId, String turnId, ToolResult result) {
        eventBus.publish(new ToolCallCompleted(taskId, turnId, result));
    }

    @Override public void hitlNotified(String taskId, String stepId, HitlCheckpoint checkpoint) {
        eventBus.publish(new HitlNotified(taskId, stepId, checkpoint));
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
