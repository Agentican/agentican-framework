package ai.agentican.temporal.workflow;

import ai.agentican.framework.agent.AgentLoopHost;
import ai.agentican.framework.agent.AgentStatus;
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
import ai.agentican.framework.hitl.HitlResponse;
import ai.agentican.framework.knowledge.KnowledgeEntry;
import ai.agentican.framework.llm.LlmRequest;
import ai.agentican.framework.llm.LlmResponse;
import ai.agentican.framework.llm.TokenUsage;
import ai.agentican.framework.llm.ToolCall;
import ai.agentican.framework.orchestration.model.WorkflowDefinition;
import ai.agentican.framework.state.RuntimeOwner;
import ai.agentican.framework.state.WorkflowRunLog;
import ai.agentican.framework.tools.ToolResult;
import ai.agentican.framework.tools.Toolkit;
import ai.agentican.temporal.activity.AgenticanActivity;
import ai.agentican.temporal.activity.KnowledgeStoreActivity;
import ai.agentican.temporal.activity.LlmCallActivity;
import ai.agentican.temporal.activity.ToolCallActivity;
import ai.agentican.temporal.dto.ToolCallRequest;

import io.temporal.activity.ActivityOptions;
import io.temporal.workflow.CancellationScope;
import io.temporal.workflow.Workflow;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

public class TemporalAgentLoopHost implements AgentLoopHost {

    @FunctionalInterface
    public interface HitlChannel {

        HitlResponse awaitResponse(String checkpointId);
    }

    private static final Duration DEFAULT_LLM_TIMEOUT   = Duration.ofMinutes(10);
    private static final Duration DEFAULT_TOOL_TIMEOUT  = Duration.ofMinutes(2);
    private static final Duration DEFAULT_STORE_TIMEOUT = Duration.ofSeconds(30);

    private final AgenticanEventBus eventBus;
    private final LlmCallActivity llmActivity;
    private final ToolCallActivity toolActivity;
    private final AgenticanActivity agenticanActivity;
    private final KnowledgeStoreActivity knowledgeActivity;
    private final HitlChannel hitlChannel;

    public TemporalAgentLoopHost(AgenticanEventBus eventBus, AgenticanActivity agenticanActivity,
                                 HitlChannel hitlChannel) {

        this(
                eventBus,
                Workflow.newActivityStub(LlmCallActivity.class,
                        ActivityOptions.newBuilder().setStartToCloseTimeout(DEFAULT_LLM_TIMEOUT).build()),
                Workflow.newActivityStub(ToolCallActivity.class,
                        ActivityOptions.newBuilder().setStartToCloseTimeout(DEFAULT_TOOL_TIMEOUT).build()),
                agenticanActivity,
                Workflow.newActivityStub(KnowledgeStoreActivity.class,
                        ActivityOptions.newBuilder().setStartToCloseTimeout(DEFAULT_STORE_TIMEOUT).build()),
                hitlChannel);
    }

    public TemporalAgentLoopHost(AgenticanEventBus eventBus,
                                 LlmCallActivity llmActivity, ToolCallActivity toolActivity,
                                 AgenticanActivity agenticanActivity, KnowledgeStoreActivity knowledgeActivity,
                                 HitlChannel hitlChannel) {

        this.eventBus = Objects.requireNonNull(eventBus, "eventBus");
        this.llmActivity = Objects.requireNonNull(llmActivity, "llmActivity");
        this.toolActivity = Objects.requireNonNull(toolActivity, "toolActivity");
        this.agenticanActivity = Objects.requireNonNull(agenticanActivity, "agenticanActivity");
        this.knowledgeActivity = Objects.requireNonNull(knowledgeActivity, "knowledgeActivity");
        this.hitlChannel = hitlChannel;
    }

    @Override
    public LlmResponse callLlm(LlmRequest request) {

        return llmActivity.send(request);
    }

    @Override
    public String executeTool(String toolName, Map<String, Object> args, Toolkit toolkit) {

        return toolActivity.execute(new ToolCallRequest(toolName, args));
    }

    @Override
    public Instant now() {

        return Instant.ofEpochMilli(Workflow.currentTimeMillis());
    }

    @Override
    public String newId() {

        return Workflow.randomUUID().toString();
    }

    @Override
    public WorkflowRunLog loadRunLog(String taskId) {

        return agenticanActivity.loadRunLog(taskId);
    }

    @Override
    public void taskStarted(String taskId, String taskName, WorkflowDefinition plan, Map<String, String> params) {

        eventBus.publish(new TaskStarted(taskId, taskName, plan, params, null, null, 0,
                RuntimeOwner.TEMPORAL, Workflow.getInfo().getWorkflowId()));
    }

    @Override
    public void stepStarted(String taskId, String stepId, String stepName) {

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

        var id = Workflow.randomUUID().toString();
        var description = "Tool call: " + call.name();
        var content = call.args() == null ? "" : call.args().toString();

        return new HitlCheckpoint(id, HitlCheckpoint.Type.TOOL_CALL, stepName, description, content);
    }

    @Override
    public HitlCheckpoint createQuestionCheckpoint(String question, String context, String stepName) {

        var id = Workflow.randomUUID().toString();

        return new HitlCheckpoint(id, HitlCheckpoint.Type.QUESTION, stepName, question, context);
    }

    @Override
    public HitlResponse awaitHitlResponse(HitlCheckpoint checkpoint) {

        if (hitlChannel == null)
            throw new IllegalStateException(
                    "awaitHitlResponse called but no HitlChannel was supplied to TemporalAgentLoopHost");

        return hitlChannel.awaitResponse(checkpoint.id());
    }

    @Override
    public KnowledgeEntry knowledgeEntry(String entryId) {

        return knowledgeActivity.get(entryId);
    }

    @Override
    public boolean isCancelled() {

        // Delegate to Temporal's cancellation state so SmacAgentRunner's mid-loop
        // host.isCancelled() checks short-circuit on workflow cancellation.
        return CancellationScope.current().isCancelRequested();
    }

    @Override
    public boolean isManaged() {

        // Temporal enforces per-step deadlines via activity / workflow execution
        // timeouts. The framework's wall-clock watchdog inside SmacAgentRunner would
        // race against those and produce inconsistent termination — defer to Temporal.
        return true;
    }
}
