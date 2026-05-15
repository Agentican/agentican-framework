package ai.agentican.temporal.workflow;

import ai.agentican.framework.agent.AgentLoopHost;
import ai.agentican.framework.hitl.HitlCheckpoint;
import ai.agentican.framework.hitl.HitlResponse;
import ai.agentican.framework.knowledge.KnowledgeEntry;
import ai.agentican.framework.llm.LlmRequest;
import ai.agentican.framework.llm.LlmResponse;
import ai.agentican.framework.llm.ToolCall;
import ai.agentican.framework.orchestration.model.WorkflowDefinition;
import ai.agentican.framework.state.WorkflowRunLog;
import ai.agentican.framework.tools.ToolResult;
import ai.agentican.framework.tools.Toolkit;
import ai.agentican.temporal.activity.KnowledgeStoreActivity;
import ai.agentican.temporal.activity.LlmCallActivity;
import ai.agentican.temporal.activity.ToolCallActivity;
import ai.agentican.temporal.activity.WorkflowRunStoreActivity;
import ai.agentican.temporal.dto.TaskStartedRequest;
import ai.agentican.temporal.dto.ToolCallRequest;

import io.temporal.activity.ActivityOptions;
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

    private final LlmCallActivity llmActivity;
    private final ToolCallActivity toolActivity;
    private final WorkflowRunStoreActivity storeActivity;
    private final KnowledgeStoreActivity knowledgeActivity;
    private final HitlChannel hitlChannel;

    public TemporalAgentLoopHost(HitlChannel hitlChannel) {

        this(
                Workflow.newActivityStub(LlmCallActivity.class,
                        ActivityOptions.newBuilder().setStartToCloseTimeout(DEFAULT_LLM_TIMEOUT).build()),
                Workflow.newActivityStub(ToolCallActivity.class,
                        ActivityOptions.newBuilder().setStartToCloseTimeout(DEFAULT_TOOL_TIMEOUT).build()),
                Workflow.newActivityStub(WorkflowRunStoreActivity.class,
                        ActivityOptions.newBuilder().setStartToCloseTimeout(DEFAULT_STORE_TIMEOUT).build()),
                Workflow.newActivityStub(KnowledgeStoreActivity.class,
                        ActivityOptions.newBuilder().setStartToCloseTimeout(DEFAULT_STORE_TIMEOUT).build()),
                hitlChannel);
    }

    public TemporalAgentLoopHost(LlmCallActivity llmActivity, ToolCallActivity toolActivity,
                                 WorkflowRunStoreActivity storeActivity, KnowledgeStoreActivity knowledgeActivity,
                                 HitlChannel hitlChannel) {

        this.llmActivity = Objects.requireNonNull(llmActivity, "llmActivity");
        this.toolActivity = Objects.requireNonNull(toolActivity, "toolActivity");
        this.storeActivity = Objects.requireNonNull(storeActivity, "storeActivity");
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

        return storeActivity.load(taskId);
    }

    @Override
    public void taskStarted(String taskId, String taskName, WorkflowDefinition plan, Map<String, String> params) {

        storeActivity.taskStarted(new TaskStartedRequest(taskId, taskName, plan, params));
    }

    @Override
    public void stepStarted(String taskId, String stepId, String stepName) {

        storeActivity.stepStarted(taskId, stepId, stepName);
    }

    @Override public void runStarted(String taskId, String stepId, String runId, String agentName) {

        storeActivity.runStarted(taskId, stepId, runId, agentName);
    }

    @Override public void runCompleted(String taskId, String runId) {

        storeActivity.runCompleted(taskId, runId);
    }

    @Override public void turnStarted(String taskId, String runId, String turnId) {

        storeActivity.turnStarted(taskId, runId, turnId);
    }

    @Override public void turnCompleted(String taskId, String turnId) {

        storeActivity.turnCompleted(taskId, turnId);
    }

    @Override public void turnAbandoned(String taskId, String turnId) {

        storeActivity.turnAbandoned(taskId, turnId);
    }

    @Override public void messageSent(String taskId, String turnId, LlmRequest request) {

        storeActivity.messageSent(taskId, turnId, request);
    }

    @Override public void responseReceived(String taskId, String turnId, LlmResponse response) {

        storeActivity.responseReceived(taskId, turnId, response);
    }

    @Override public void toolCallStarted(String taskId, String turnId, ToolCall toolCall) {

        storeActivity.toolCallStarted(taskId, turnId, toolCall);
    }

    @Override public void toolCallCompleted(String taskId, String turnId, ToolResult result) {

        storeActivity.toolCallCompleted(taskId, turnId, result);
    }

    @Override public void hitlNotified(String taskId, String stepId, HitlCheckpoint checkpoint) {

        storeActivity.hitlNotified(taskId, stepId, checkpoint);
    }

    @Override public void hitlResponded(String taskId, String stepId, HitlResponse response) {

        storeActivity.hitlResponded(taskId, stepId, response);
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

        return false;
    }
}
