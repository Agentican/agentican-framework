package ai.agentican.quarkus.otel;

import ai.agentican.framework.TaskListener;
import ai.agentican.framework.agent.AgentStatus;
import ai.agentican.framework.hitl.HitlCheckpointType;
import ai.agentican.framework.llm.StopReason;
import ai.agentican.framework.state.StepLog;
import ai.agentican.framework.orchestration.execution.TaskStatus;

import ai.agentican.framework.state.TaskStateStore;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;

import java.util.concurrent.ConcurrentHashMap;

public class TracedLifecycleListener implements TaskListener {

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
    private final TaskStateStore taskStateStore;
    private final ConcurrentHashMap<String, SpanAndScope> spans = new ConcurrentHashMap<>();
    private final java.util.Set<String> resumedTaskIds = java.util.concurrent.ConcurrentHashMap.newKeySet();

    public TracedLifecycleListener(Tracer tracer, ai.agentican.framework.state.TaskStateStore taskStateStore) {

        this.tracer = tracer;
        this.taskStateStore = taskStateStore;
    }

    @Override
    public void onTaskResumed(String taskId) {

        resumedTaskIds.add(taskId);
    }

    private void stampResumedIfApplicable(io.opentelemetry.api.trace.SpanBuilder builder, String taskId) {

        if (resumedTaskIds.contains(taskId)) builder.setAttribute(RESUMED, true);
    }

    @Override
    public void onTaskStarted(String taskId) {

        var taskLog = taskStateStore.load(taskId);

        var builder = tracer.spanBuilder(TASK_SPAN).setAttribute(TASK_ID, taskId);
        stampResumedIfApplicable(builder, taskId);

        if (taskLog != null && taskLog.createdAt() != null)
            builder.setStartTimestamp(taskLog.createdAt());

        if (taskLog != null && taskLog.parentTaskId() != null && taskLog.parentStepId() != null) {

            var parentEntry = spans.get(stepKey(taskLog.parentTaskId(), taskLog.parentStepId()));

            if (parentEntry != null)
                builder.setParent(Context.current().with(parentEntry.span));
        }

        var span = builder.startSpan();

        spans.put(taskKey(taskId), new SpanAndScope(span, span.makeCurrent()));
    }

    @Override
    public void onTaskCompleted(String taskId, TaskStatus status) {

        resumedTaskIds.remove(taskId);

        var entry = spans.remove(taskKey(taskId));

        if (entry == null) return;

        if (status == TaskStatus.FAILED || status == TaskStatus.CANCELLED)
            entry.span.setStatus(StatusCode.ERROR, status.name());
        else
            entry.span.setStatus(StatusCode.OK);

        var taskLog = taskStateStore.load(taskId);

        if (taskLog != null && taskLog.completedAt() != null) {

            entry.span.end(taskLog.completedAt());
            entry.scope.close();
        }
        else
            entry.close();
    }

    @Override
    public void onStepStarted(String taskId, String stepId) {

        var step = resolveStepById(taskId, stepId);
        var stepName = step != null ? step.stepName() : stepId;

        var builder = tracer.spanBuilder(STEP_SPAN_PREFIX + stepName)
                .setAttribute(TASK_ID, taskId)
                .setAttribute(STEP_NAME, stepName);

        if (step != null && step.createdAt() != null)
            builder.setStartTimestamp(step.createdAt());

        stampResumedIfApplicable(builder, taskId);

        var span = builder.startSpan();

        spans.put(stepKey(taskId, stepId), new SpanAndScope(span, span.makeCurrent()));
    }

    @Override
    public void onStepCompleted(String taskId, String stepId) {

        var entry = spans.remove(stepKey(taskId, stepId));

        if (entry == null) return;

        var step = resolveStepById(taskId, stepId);
        var status = step != null ? step.status() : null;

        if (status != null) {

            entry.span.setAttribute(STEP_STATUS, status.name());

            if (status == TaskStatus.FAILED || status == TaskStatus.CANCELLED)
                entry.span.setStatus(StatusCode.ERROR, status.name());
            else
                entry.span.setStatus(StatusCode.OK);
        }
        else
            entry.span.setStatus(StatusCode.OK);

        if (step != null && step.completedAt() != null) {

            entry.span.end(step.completedAt());
            entry.scope.close();
        }
        else
            entry.close();
    }

    @Override
    public void onRunStarted(String taskId, String runId) {

        var taskLog = taskStateStore.load(taskId);
        var run = taskLog != null ? taskLog.findRunById(runId) : null;
        var agentName = run != null && run.agentName() != null ? run.agentName() : "unknown";

        var runBuilder = tracer.spanBuilder(RUN_SPAN).setAttribute(AGENT_NAME, agentName);
        stampResumedIfApplicable(runBuilder, taskId);
        var span = runBuilder.startSpan();

        spans.put(runKey(taskId, runId), new SpanAndScope(span, span.makeCurrent()));
    }

    @Override
    public void onRunCompleted(String taskId, String runId, AgentStatus status) {

        var entry = spans.remove(runKey(taskId, runId));

        if (entry == null) return;

        entry.span.setAttribute(AGENT_STATUS, status.name());

        if (status == AgentStatus.COMPLETED)
            entry.span.setStatus(StatusCode.OK);
        else
            entry.span.setStatus(StatusCode.ERROR, status.name());

        entry.close();
    }

    @Override
    public void onTurnStarted(String taskId, String turnId) {

        var taskLog = taskStateStore.load(taskId);
        var turnLog = taskLog != null ? taskLog.findTurnById(turnId) : null;
        var turnIndex = turnLog != null ? turnLog.index() : 0;

        var agentName = "unknown";

        if (taskLog != null) {

            outer:
            for (var step : taskLog.steps().values()) {

                for (var run : step.runs()) {

                    for (var turn : run.turns()) {

                        if (turnId.equals(turn.id())) {

                            agentName = run.agentName() != null ? run.agentName() : "unknown";

                            break outer;
                        }
                    }
                }
            }
        }

        var builder = tracer.spanBuilder(TURN_SPAN_PREFIX + turnIndex)
                .setAttribute(AGENT_NAME, agentName)
                .setAttribute(TURN_INDEX, (long) turnIndex);

        if (turnLog != null && turnLog.startedAt() != null)
            builder.setStartTimestamp(turnLog.startedAt());

        stampResumedIfApplicable(builder, taskId);

        var span = builder.startSpan();

        spans.put(turnKey(taskId, turnId), new SpanAndScope(span, span.makeCurrent()));
    }

    @Override
    public void onTurnCompleted(String taskId, String turnId) {

        var entry = spans.remove(turnKey(taskId, turnId));

        if (entry == null) return;

        entry.span.setStatus(StatusCode.OK);

        var taskLog = taskStateStore.load(taskId);
        var turnLog = taskLog != null ? taskLog.findTurnById(turnId) : null;

        if (turnLog != null && turnLog.completedAt() != null) {

            entry.span.end(turnLog.completedAt());
            entry.scope.close();
        }
        else
            entry.close();
    }

    @Override
    public void onMessageSent(String taskId, String turnId) {

        var taskLog = taskStateStore.load(taskId);
        var turnLog = taskLog != null ? taskLog.findTurnById(turnId) : null;
        var request = turnLog != null ? turnLog.request() : null;

        var builder = tracer.spanBuilder(LLM_SPAN).setSpanKind(SpanKind.CLIENT);

        if (request != null) {

            if (request.provider() != null) builder.setAttribute(GEN_AI_SYSTEM, request.provider());
            if (request.model() != null) builder.setAttribute(GEN_AI_MODEL, request.model());
            if (request.llmName() != null) builder.setAttribute(LLM_NAME, request.llmName());
        }

        stampResumedIfApplicable(builder, taskId);

        var span = builder.startSpan();

        spans.put(llmKey(taskId, turnId), new SpanAndScope(span, span.makeCurrent()));
    }

    @Override
    public void onResponseReceived(String taskId, String turnId, StopReason stopReason) {

        var entry = spans.remove(llmKey(taskId, turnId));

        if (entry == null) return;

        var taskLog = taskStateStore.load(taskId);
        var turnLog = taskLog != null ? taskLog.findTurnById(turnId) : null;
        var response = turnLog != null ? turnLog.response() : null;

        if (response != null) {

            entry.span.setAttribute(GEN_AI_INPUT_TOKENS, response.inputTokens());
            entry.span.setAttribute(GEN_AI_OUTPUT_TOKENS, response.outputTokens());
            entry.span.setAttribute(GEN_AI_CACHE_READ, response.cacheReadTokens());
            entry.span.setAttribute(GEN_AI_CACHE_WRITE, response.cacheWriteTokens());
            entry.span.setAttribute(TURN_INPUT_TOKENS, response.inputTokens());
            entry.span.setAttribute(TURN_OUTPUT_TOKENS, response.outputTokens());
        }

        entry.span.setAttribute(GEN_AI_FINISH, stopReason.name());
        entry.span.setAttribute(STOP_REASON, stopReason.name());
        entry.span.setStatus(StatusCode.OK);

        entry.close();
    }

    @Override
    public void onToolCallStarted(String taskId, String toolCallId) {

        var taskLog = taskStateStore.load(taskId);
        var toolName = resolveToolNameByCallId(taskLog, toolCallId);

        var toolBuilder = tracer.spanBuilder(TOOL_SPAN).setAttribute(TOOL_NAME, toolName);
        stampResumedIfApplicable(toolBuilder, taskId);
        var span = toolBuilder.startSpan();

        spans.put(toolKey(taskId, toolCallId), new SpanAndScope(span, span.makeCurrent()));
    }

    @Override
    public void onToolCallCompleted(String taskId, String toolCallId) {

        var entry = spans.remove(toolKey(taskId, toolCallId));

        if (entry == null) return;

        var taskLog = taskStateStore.load(taskId);
        var toolResult = resolveToolResultByCallId(taskLog, toolCallId);

        if (toolResult != null && toolResult.isError()) {

            entry.span.setStatus(StatusCode.ERROR, "Tool execution failed");

            if (toolResult.cause() != null) entry.span.recordException(toolResult.cause());
        }
        else
            entry.span.setStatus(StatusCode.OK);

        entry.close();
    }

    @Override
    public void onHitlNotified(String taskId, String hitlId, HitlCheckpointType type) {

        var hitlBuilder = tracer.spanBuilder(HITL_SPAN)
                .setAttribute(HITL_CHECKPOINT_ID, hitlId)
                .setAttribute(HITL_CHECKPOINT_TYPE, type.name());
        stampResumedIfApplicable(hitlBuilder, taskId);
        var span = hitlBuilder.startSpan();

        spans.put(hitlKey(taskId, hitlId), new SpanAndScope(span, span.makeCurrent()));
    }

    @Override
    public void onHitlResponded(String taskId, String hitlId, boolean approved) {

        var entry = spans.remove(hitlKey(taskId, hitlId));

        if (entry == null) return;

        entry.span.setStatus(StatusCode.OK);
        entry.close();
    }

    private StepLog resolveStepById(String taskId, String stepId) {

        var taskLog = taskStateStore.load(taskId);

        return taskLog != null ? taskLog.findStepById(stepId) : null;
    }

    private static String resolveToolNameByCallId(ai.agentican.framework.state.TaskLog taskLog, String toolCallId) {

        if (taskLog == null) return "unknown";

        for (var step : taskLog.steps().values()) {

            for (var run : step.runs()) {

                for (var turn : run.turns()) {

                    if (turn.response() != null && turn.response().toolCalls() != null) {

                        for (var tc : turn.response().toolCalls()) {

                            if (toolCallId.equals(tc.id())) return tc.toolName();
                        }
                    }
                }
            }
        }

        return "unknown";
    }

    private static ai.agentican.framework.tools.ToolResult resolveToolResultByCallId(
            ai.agentican.framework.state.TaskLog taskLog, String toolCallId) {

        if (taskLog == null) return null;

        for (var step : taskLog.steps().values()) {

            for (var run : step.runs()) {

                for (var turn : run.turns()) {

                    for (var tr : turn.toolResults()) {

                        if (toolCallId.equals(tr.toolCallId())) return tr;
                    }
                }
            }
        }

        return null;
    }

    private static String taskKey(String taskId) { return taskId + ":task"; }
    private static String stepKey(String taskId, String stepId) { return taskId + ":step:" + stepId; }
    private static String runKey(String taskId, String runId) { return taskId + ":run:" + runId; }
    private static String turnKey(String taskId, String turnId) { return taskId + ":turn:" + turnId; }
    private static String llmKey(String taskId, String turnId) { return taskId + ":llm:" + turnId; }
    private static String toolKey(String taskId, String toolCallId) { return taskId + ":tool:" + toolCallId; }
    private static String hitlKey(String taskId, String checkpointId) { return taskId + ":hitl:" + checkpointId; }

    private record SpanAndScope(Span span, Scope scope) {

        void close() {

            scope.close();
            span.end();
        }
    }
}
