package ai.agentican.quarkus.otel;

import ai.agentican.framework.agent.AgentStatus;
import ai.agentican.framework.event.AgenticanEvent;
import ai.agentican.framework.event.AgenticanEventListener;
import ai.agentican.framework.event.HitlNotified;
import ai.agentican.framework.event.HitlResponded;
import ai.agentican.framework.event.MessageSent;
import ai.agentican.framework.event.ResponseReceived;
import ai.agentican.framework.event.RunCompleted;
import ai.agentican.framework.event.RunStarted;
import ai.agentican.framework.event.StepCompleted;
import ai.agentican.framework.event.StepStarted;
import ai.agentican.framework.event.TaskCompleted;
import ai.agentican.framework.event.TaskResumed;
import ai.agentican.framework.event.TaskStarted;
import ai.agentican.framework.event.ToolCallCompleted;
import ai.agentican.framework.event.ToolCallStarted;
import ai.agentican.framework.event.TurnCompleted;
import ai.agentican.framework.event.TurnStarted;
import ai.agentican.framework.orchestration.execution.WorkflowRunStatus;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Subscribes to {@link AgenticanEvent}s and opens / closes OTel spans for
 * tasks, steps, runs, turns, LLM calls, tool calls, and HITL checkpoints.
 *
 * <p>All span attributes come from event payloads — no {@code store.load()}
 * inside event handlers (event-payload-sufficiency principle). Agent name
 * is tracked per-run via a small {@link ConcurrentHashMap} keyed by
 * {@code runId} (populated on {@link RunStarted}, cleared on
 * {@link RunCompleted}) so {@link TurnStarted} spans can be labelled
 * without back-references to the store.
 *
 * <p>Span timestamps default to OTel's {@code Instant.now()} rather than
 * the framework's persisted createdAt/completedAt timestamps — a small
 * fidelity loss vs. the previous {@code store.load()} pattern, in
 * exchange for keeping observability event-driven.
 */
public class TracedLifecycleListener implements AgenticanEventListener {

    private static final String TASK_SPAN = "agentican.task";
    private static final String STEP_SPAN_PREFIX = "agentican.step ";
    private static final String RUN_SPAN = "agentican.run";
    private static final String TURN_SPAN_PREFIX = "agentican.turn ";
    private static final String LLM_SPAN = "agentican.llm.call";
    private static final String TOOL_SPAN = "agentican.tool.call";
    private static final String HITL_SPAN = "agentican.hitl.wait";

    private static final AttributeKey<String> TASK_ID = AttributeKey.stringKey("agentican.task.id");
    private static final AttributeKey<String> STEP_NAME = AttributeKey.stringKey("agentican.step.name");
    private static final AttributeKey<String> STEP_STATUS = AttributeKey.stringKey("agentican.step.status");
    private static final AttributeKey<String> AGENT_NAME = AttributeKey.stringKey("agentican.agent.name");
    private static final AttributeKey<String> AGENT_STATUS = AttributeKey.stringKey("agentican.agent.status");
    private static final AttributeKey<Long> TURN_INDEX = AttributeKey.longKey("agentican.turn.index");
    private static final AttributeKey<String> STOP_REASON = AttributeKey.stringKey("agentican.turn.stop_reason");
    private static final AttributeKey<Long> TURN_INPUT_TOKENS = AttributeKey.longKey("agentican.turn.input_tokens");
    private static final AttributeKey<Long> TURN_OUTPUT_TOKENS = AttributeKey.longKey("agentican.turn.output_tokens");
    private static final AttributeKey<String> LLM_NAME = AttributeKey.stringKey("agentican.llm.name");
    private static final AttributeKey<String> TOOL_NAME = AttributeKey.stringKey("agentican.tool.name");
    private static final AttributeKey<String> HITL_CHECKPOINT_ID = AttributeKey.stringKey("agentican.hitl.checkpoint.id");
    private static final AttributeKey<String> HITL_CHECKPOINT_TYPE = AttributeKey.stringKey("agentican.hitl.checkpoint.type");

    private static final AttributeKey<Boolean> RESUMED = AttributeKey.booleanKey("agentican.resumed");

    private static final AttributeKey<String> GEN_AI_SYSTEM = AttributeKey.stringKey("gen_ai.system");
    private static final AttributeKey<String> GEN_AI_MODEL = AttributeKey.stringKey("gen_ai.request.model");
    private static final AttributeKey<Long> GEN_AI_INPUT_TOKENS = AttributeKey.longKey("gen_ai.usage.input_tokens");
    private static final AttributeKey<Long> GEN_AI_OUTPUT_TOKENS = AttributeKey.longKey("gen_ai.usage.output_tokens");
    private static final AttributeKey<Long> GEN_AI_CACHE_READ = AttributeKey.longKey("gen_ai.usage.cache_read_tokens");
    private static final AttributeKey<Long> GEN_AI_CACHE_WRITE = AttributeKey.longKey("gen_ai.usage.cache_write_tokens");
    private static final AttributeKey<String> GEN_AI_FINISH = AttributeKey.stringKey("gen_ai.response.finish_reasons");

    private final Tracer tracer;

    private final ConcurrentHashMap<String, SpanAndScope> spans = new ConcurrentHashMap<>();
    private final java.util.Set<String> resumedTaskIds = ConcurrentHashMap.newKeySet();

    // Per-run agent name, set on RunStarted, used by TurnStarted spans within that run.
    private final ConcurrentHashMap<String, String> runAgentNames = new ConcurrentHashMap<>();
    // Per-turn agent name, set on TurnStarted (resolved via runAgentNames), used by tool spans.
    private final ConcurrentHashMap<String, String> turnAgentNames = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> turnToRun = new ConcurrentHashMap<>();
    // Per-tool-call name for completion lookup.
    private final ConcurrentHashMap<String, String> toolNames = new ConcurrentHashMap<>();

    public TracedLifecycleListener(Tracer tracer) {

        this.tracer = tracer;
    }

    @Override
    public void on(AgenticanEvent event) {

        switch (event) {

            case TaskResumed       e -> resumedTaskIds.add(e.taskId());
            case TaskStarted       e -> onTaskStarted(e);
            case TaskCompleted     e -> onTaskCompleted(e);
            case StepStarted       e -> onStepStarted(e);
            case StepCompleted     e -> onStepCompleted(e);
            case RunStarted        e -> onRunStarted(e);
            case RunCompleted      e -> onRunCompleted(e);
            case TurnStarted       e -> onTurnStarted(e);
            case TurnCompleted     e -> onTurnCompleted(e);
            case MessageSent       e -> onMessageSent(e);
            case ResponseReceived  e -> onResponseReceived(e);
            case ToolCallStarted   e -> onToolCallStarted(e);
            case ToolCallCompleted e -> onToolCallCompleted(e);
            case HitlNotified      e -> onHitlNotified(e);
            case HitlResponded     e -> onHitlResponded(e);
            default -> { /* events with no span mapping */ }
        }
    }

    private void stampResumedIfApplicable(io.opentelemetry.api.trace.SpanBuilder builder, String taskId) {

        if (resumedTaskIds.contains(taskId)) builder.setAttribute(RESUMED, true);
    }

    private void onTaskStarted(TaskStarted event) {

        var builder = tracer.spanBuilder(TASK_SPAN).setAttribute(TASK_ID, event.taskId());

        stampResumedIfApplicable(builder, event.taskId());

        // Parent linking: if this is a sub-task whose parent step's span is open, attach.
        if (event.parentTaskId() != null && event.parentStepId() != null) {

            var parentEntry = spans.get(stepKey(event.parentTaskId(), event.parentStepId()));

            if (parentEntry != null) builder.setParent(Context.current().with(parentEntry.span));
        }

        var span = builder.startSpan();

        spans.put(taskKey(event.taskId()), new SpanAndScope(span, span.makeCurrent()));
    }

    private void onTaskCompleted(TaskCompleted event) {

        resumedTaskIds.remove(event.taskId());

        var entry = spans.remove(taskKey(event.taskId()));

        if (entry == null) return;

        if (event.status() == WorkflowRunStatus.FAILED || event.status() == WorkflowRunStatus.CANCELLED)
            entry.span.setStatus(StatusCode.ERROR, event.status().name());
        else
            entry.span.setStatus(StatusCode.OK);

        entry.close();
    }

    private void onStepStarted(StepStarted event) {

        var stepName = event.stepName() != null ? event.stepName() : event.stepId();

        var builder = tracer.spanBuilder(STEP_SPAN_PREFIX + stepName)
                .setAttribute(TASK_ID, event.taskId())
                .setAttribute(STEP_NAME, stepName);

        stampResumedIfApplicable(builder, event.taskId());

        var span = builder.startSpan();

        spans.put(stepKey(event.taskId(), event.stepId()), new SpanAndScope(span, span.makeCurrent()));
    }

    private void onStepCompleted(StepCompleted event) {

        var entry = spans.remove(stepKey(event.taskId(), event.stepId()));

        if (entry == null) return;

        if (event.status() != null) {

            entry.span.setAttribute(STEP_STATUS, event.status().name());

            if (event.status() == WorkflowRunStatus.FAILED || event.status() == WorkflowRunStatus.CANCELLED)
                entry.span.setStatus(StatusCode.ERROR, event.status().name());
            else
                entry.span.setStatus(StatusCode.OK);
        }
        else
            entry.span.setStatus(StatusCode.OK);

        entry.close();
    }

    private void onRunStarted(RunStarted event) {

        var agentName = event.agentName() != null ? event.agentName() : "unknown";

        runAgentNames.put(event.runId(), agentName);

        var runBuilder = tracer.spanBuilder(RUN_SPAN).setAttribute(AGENT_NAME, agentName);

        stampResumedIfApplicable(runBuilder, event.taskId());

        var span = runBuilder.startSpan();

        spans.put(runKey(event.taskId(), event.runId()), new SpanAndScope(span, span.makeCurrent()));
    }

    private void onRunCompleted(RunCompleted event) {

        var entry = spans.remove(runKey(event.taskId(), event.runId()));

        runAgentNames.remove(event.runId());

        if (entry == null) return;

        var status = event.status() != null ? event.status() : AgentStatus.COMPLETED;

        entry.span.setAttribute(AGENT_STATUS, status.name());

        if (status == AgentStatus.COMPLETED)
            entry.span.setStatus(StatusCode.OK);
        else
            entry.span.setStatus(StatusCode.ERROR, status.name());

        entry.close();
    }

    private void onTurnStarted(TurnStarted event) {

        var agentName = runAgentNames.getOrDefault(event.runId(), "unknown");

        turnToRun.put(event.turnId(), event.runId());
        turnAgentNames.put(event.turnId(), agentName);

        var builder = tracer.spanBuilder(TURN_SPAN_PREFIX + event.index())
                .setAttribute(AGENT_NAME, agentName)
                .setAttribute(TURN_INDEX, (long) event.index());

        stampResumedIfApplicable(builder, event.taskId());

        var span = builder.startSpan();

        spans.put(turnKey(event.taskId(), event.turnId()), new SpanAndScope(span, span.makeCurrent()));
    }

    private void onTurnCompleted(TurnCompleted event) {

        turnToRun.remove(event.turnId());
        turnAgentNames.remove(event.turnId());

        var entry = spans.remove(turnKey(event.taskId(), event.turnId()));

        if (entry == null) return;

        entry.span.setStatus(StatusCode.OK);
        entry.close();
    }

    private void onMessageSent(MessageSent event) {

        var builder = tracer.spanBuilder(LLM_SPAN).setSpanKind(SpanKind.CLIENT);

        var request = event.request();

        if (request != null) {

            if (request.provider() != null) builder.setAttribute(GEN_AI_SYSTEM, request.provider());
            if (request.model() != null)    builder.setAttribute(GEN_AI_MODEL, request.model());
            if (request.llmName() != null)  builder.setAttribute(LLM_NAME, request.llmName());
        }

        stampResumedIfApplicable(builder, event.taskId());

        var span = builder.startSpan();

        spans.put(llmKey(event.taskId(), event.turnId()), new SpanAndScope(span, span.makeCurrent()));
    }

    private void onResponseReceived(ResponseReceived event) {

        var entry = spans.remove(llmKey(event.taskId(), event.turnId()));

        if (entry == null) return;

        var response = event.response();

        if (response != null) {

            entry.span.setAttribute(GEN_AI_INPUT_TOKENS, response.inputTokens());
            entry.span.setAttribute(GEN_AI_OUTPUT_TOKENS, response.outputTokens());
            entry.span.setAttribute(GEN_AI_CACHE_READ, response.cacheReadTokens());
            entry.span.setAttribute(GEN_AI_CACHE_WRITE, response.cacheWriteTokens());
            entry.span.setAttribute(TURN_INPUT_TOKENS, response.inputTokens());
            entry.span.setAttribute(TURN_OUTPUT_TOKENS, response.outputTokens());

            if (response.stopReason() != null) {
                entry.span.setAttribute(GEN_AI_FINISH, response.stopReason().name());
                entry.span.setAttribute(STOP_REASON, response.stopReason().name());
            }
        }

        entry.span.setStatus(StatusCode.OK);
        entry.close();
    }

    private void onToolCallStarted(ToolCallStarted event) {

        var toolName = event.toolCall() != null && event.toolCall().name() != null
                ? event.toolCall().name() : "unknown";

        toolNames.put(event.toolCall().id(), toolName);

        var toolBuilder = tracer.spanBuilder(TOOL_SPAN).setAttribute(TOOL_NAME, toolName);

        stampResumedIfApplicable(toolBuilder, event.taskId());

        var span = toolBuilder.startSpan();

        spans.put(toolKey(event.taskId(), event.toolCall().id()), new SpanAndScope(span, span.makeCurrent()));
    }

    private void onToolCallCompleted(ToolCallCompleted event) {

        var result = event.toolResult();

        var entry = spans.remove(toolKey(event.taskId(), result.toolCallId()));

        toolNames.remove(result.toolCallId());

        if (entry == null) return;

        if (result.isError()) {

            entry.span.setStatus(StatusCode.ERROR, "Tool execution failed");

            if (result.cause() != null) entry.span.recordException(result.cause());
        }
        else
            entry.span.setStatus(StatusCode.OK);

        entry.close();
    }

    private void onHitlNotified(HitlNotified event) {

        var cp = event.checkpoint();

        var hitlBuilder = tracer.spanBuilder(HITL_SPAN)
                .setAttribute(HITL_CHECKPOINT_ID, cp.id())
                .setAttribute(HITL_CHECKPOINT_TYPE, cp.type().name());

        stampResumedIfApplicable(hitlBuilder, event.taskId());

        var span = hitlBuilder.startSpan();

        spans.put(hitlKey(event.taskId(), cp.id()), new SpanAndScope(span, span.makeCurrent()));
    }

    private void onHitlResponded(HitlResponded event) {

        var entry = spans.remove(hitlKey(event.taskId(), event.hitlId()));

        if (entry == null) return;

        entry.span.setStatus(StatusCode.OK);
        entry.close();
    }

    private static String taskKey(String taskId)                              { return taskId + ":task"; }
    private static String stepKey(String taskId, String stepId)               { return taskId + ":step:" + stepId; }
    private static String runKey(String taskId, String runId)                 { return taskId + ":run:" + runId; }
    private static String turnKey(String taskId, String turnId)               { return taskId + ":turn:" + turnId; }
    private static String llmKey(String taskId, String turnId)                { return taskId + ":llm:" + turnId; }
    private static String toolKey(String taskId, String toolCallId)           { return taskId + ":tool:" + toolCallId; }
    private static String hitlKey(String taskId, String checkpointId)         { return taskId + ":hitl:" + checkpointId; }

    private record SpanAndScope(Span span, Scope scope) {

        void close() {

            scope.close();
            span.end();
        }
    }
}
