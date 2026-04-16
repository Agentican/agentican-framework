package ai.agentican.framework.orchestration.execution.resume;

import ai.agentican.framework.llm.ToolCall;
import ai.agentican.framework.orchestration.execution.TaskStatus;
import ai.agentican.framework.orchestration.model.Plan;
import ai.agentican.framework.orchestration.model.PlanStep;
import ai.agentican.framework.state.RunLog;
import ai.agentican.framework.state.StepLog;
import ai.agentican.framework.state.TaskLog;
import ai.agentican.framework.state.TurnLog;
import ai.agentican.framework.tools.ToolResult;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public final class ResumeClassifier {

    private ResumeClassifier() {}

    public static ResumePlan classify(TaskLog taskLog, Plan plan) {

        if (taskLog == null)
            return ResumePlan.reap(ReapReason.TASK_LOG_MISSING);

        if (plan == null)
            return ResumePlan.reap(taskLog.planSnapshotCorrupt() ? ReapReason.PLAN_CORRUPT : ReapReason.PLAN_UNAVAILABLE);

        if (taskLog.status() != null)
            return ResumePlan.reap(ReapReason.TASK_NOT_RUNNING);

        var completed = new ArrayList<StepLog>();
        StepLog inFlight = null;

        for (var step : plan.steps()) {
            var log = taskLog.step(step.name());
            if (log == null) break;
            if (log.status() == TaskStatus.COMPLETED) {
                completed.add(log);
                continue;
            }
            inFlight = log;
            break;
        }

        if (inFlight == null)
            return new ResumePlan(List.copyOf(completed), Optional.empty(), Optional.empty(), Optional.empty(),
                    TurnResumeState.NONE, List.of(), false, null);

        var lastRun = inFlight.lastRun();

        if (lastRun == null || lastRun.turns().isEmpty())
            return new ResumePlan(List.copyOf(completed), Optional.of(inFlight),
                    Optional.ofNullable(lastRun), Optional.empty(),
                    TurnResumeState.NONE, List.of(), false, null);

        var lastTurn = lastRun.turns().getLast();
        var state = classifyTurn(lastTurn);
        lastTurn.setResumeState(state);
        var pending = pendingTools(lastTurn);

        return new ResumePlan(List.copyOf(completed), Optional.of(inFlight),
                Optional.of(lastRun), Optional.of(lastTurn), state, pending, false, null);
    }

    public static TurnResumeState classifyTurn(TurnLog turn) {

        if (turn == null) return TurnResumeState.NONE;

        if (turn.state() == TurnLog.State.COMPLETED || turn.state() == TurnLog.State.ABANDONED
                || turn.completedAt() != null) {
            return TurnResumeState.CLOSED;
        }

        if (turn.request() == null) return TurnResumeState.STARTED_NO_MESSAGE;

        if (turn.response() == null) return TurnResumeState.MESSAGE_SENT;

        var expectedCalls = turn.response().toolCalls();

        if (expectedCalls == null || expectedCalls.isEmpty()) return TurnResumeState.TOOLS_COMPLETE;

        var doneIds = new LinkedHashSet<String>();
        int doneCount = 0;

        for (var tr : turn.toolResults()) {
            if (tr.state() == ToolResult.State.COMPLETED || tr.state() == ToolResult.State.FAILED) {
                doneIds.add(tr.toolCallId());
                doneCount++;
            }
        }

        if (doneCount == 0) return TurnResumeState.RESPONSE_RECEIVED;

        if (doneCount < expectedCalls.size()) return TurnResumeState.TOOLS_PARTIAL;

        return TurnResumeState.TOOLS_COMPLETE;
    }

    private static List<ToolCall> pendingTools(TurnLog turn) {

        if (turn == null || turn.response() == null) return List.of();

        var response = turn.response();
        if (response.toolCalls() == null) return List.of();

        Set<String> doneIds = new LinkedHashSet<>();
        for (var tr : turn.toolResults()) {
            if (tr.state() == ToolResult.State.COMPLETED || tr.state() == ToolResult.State.FAILED)
                doneIds.add(tr.toolCallId());
        }

        var pending = new ArrayList<ToolCall>();
        for (var call : response.toolCalls()) {
            if (!doneIds.contains(call.id())) pending.add(call);
        }
        return pending;
    }
}
