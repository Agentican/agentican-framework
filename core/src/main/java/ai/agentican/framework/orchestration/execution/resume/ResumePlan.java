package ai.agentican.framework.orchestration.execution.resume;

import ai.agentican.framework.llm.ToolCall;
import ai.agentican.framework.state.RunLog;
import ai.agentican.framework.state.StepLog;
import ai.agentican.framework.state.TurnLog;

import java.util.List;
import java.util.Optional;

public record ResumePlan(
        List<StepLog> completedSteps,
        Optional<StepLog> inFlightStep,
        Optional<RunLog> inFlightRun,
        Optional<TurnLog> inFlightTurn,
        TurnResumeState turnState,
        List<ToolCall> toolsToExecute,
        boolean reapOnly,
        ReapReason reapReason) {

    public ResumePlan {

        if (completedSteps == null) completedSteps = List.of();
        if (inFlightStep == null) inFlightStep = Optional.empty();
        if (inFlightRun == null) inFlightRun = Optional.empty();
        if (inFlightTurn == null) inFlightTurn = Optional.empty();
        if (turnState == null) turnState = TurnResumeState.NONE;
        if (toolsToExecute == null) toolsToExecute = List.of();
    }

    public static ResumePlan reap(ReapReason reason) {

        return new ResumePlan(List.of(), Optional.empty(), Optional.empty(), Optional.empty(),
                TurnResumeState.NONE, List.of(), true, reason);
    }
}
