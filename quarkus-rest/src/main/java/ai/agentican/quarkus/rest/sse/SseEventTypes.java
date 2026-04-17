package ai.agentican.quarkus.rest.sse;

import ai.agentican.quarkus.event.*;

public final class SseEventTypes {

    public static final String PLAN_STARTED = "plan_started";
    public static final String PLAN_COMPLETED = "plan_completed";
    public static final String TASK_STARTED = "task_started";
    public static final String TASK_COMPLETED = "task_completed";
    public static final String STEP_STARTED = "step_started";
    public static final String STEP_COMPLETED = "step_completed";
    public static final String RUN_STARTED = "run_started";
    public static final String RUN_COMPLETED = "run_completed";
    public static final String TURN_STARTED = "turn_started";
    public static final String TURN_COMPLETED = "turn_completed";
    public static final String MESSAGE_SENT = "message_sent";
    public static final String RESPONSE_RECEIVED = "response_received";
    public static final String TOOL_CALL_STARTED = "tool_call_started";
    public static final String TOOL_CALL_COMPLETED = "tool_call_completed";
    public static final String HITL_CHECKPOINT = "hitl_checkpoint";
    public static final String ITERATION_STARTED = "iteration_started";
    public static final String ITERATION_COMPLETED = "iteration_completed";
    public static final String HEARTBEAT = "heartbeat";
    public static final String UNKNOWN = "event";

    private SseEventTypes() {}

    public static String nameFor(Object event) {

        return switch (event) {
            case PlanStartedEvent ignored -> PLAN_STARTED;
            case PlanCompletedEvent ignored -> PLAN_COMPLETED;
            case TaskStartedEvent ignored -> TASK_STARTED;
            case TaskCompletedEvent ignored -> TASK_COMPLETED;
            case StepStartedEvent ignored -> STEP_STARTED;
            case StepCompletedEvent ignored -> STEP_COMPLETED;
            case RunStartedEvent ignored -> RUN_STARTED;
            case RunCompletedEvent ignored -> RUN_COMPLETED;
            case TurnStartedEvent ignored -> TURN_STARTED;
            case TurnCompletedEvent ignored -> TURN_COMPLETED;
            case MessageSentEvent ignored -> MESSAGE_SENT;
            case ResponseReceivedEvent ignored -> RESPONSE_RECEIVED;
            case ToolCallStartedEvent ignored -> TOOL_CALL_STARTED;
            case ToolCallCompletedEvent ignored -> TOOL_CALL_COMPLETED;
            case HitlCheckpointEvent ignored -> HITL_CHECKPOINT;
            case IterationStartedEvent ignored -> ITERATION_STARTED;
            case IterationCompletedEvent ignored -> ITERATION_COMPLETED;
            case null -> UNKNOWN;
            default -> UNKNOWN;
        };
    }
}
