package ai.agentican.quarkus.event;

import ai.agentican.framework.Agentican;
import ai.agentican.framework.event.AgenticanEvent;
import ai.agentican.framework.event.AgenticanEventListener;
import ai.agentican.framework.event.HitlNotified;
import ai.agentican.framework.event.MessageSent;
import ai.agentican.framework.event.PlanStarted;
import ai.agentican.framework.event.PlanCompleted;
import ai.agentican.framework.event.ResponseReceived;
import ai.agentican.framework.event.RunCompleted;
import ai.agentican.framework.event.RunStarted;
import ai.agentican.framework.event.StepCompleted;
import ai.agentican.framework.event.StepStarted;
import ai.agentican.framework.event.TaskCompleted;
import ai.agentican.framework.event.TaskReaped;
import ai.agentican.framework.event.TaskResumed;
import ai.agentican.framework.event.TaskStarted;
import ai.agentican.framework.event.ToolCallCompleted;
import ai.agentican.framework.event.ToolCallStarted;
import ai.agentican.framework.event.TurnCompleted;
import ai.agentican.framework.event.TurnStarted;
import ai.agentican.framework.util.Ids;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Translates {@link AgenticanEvent}s on the framework bus into CDI {@link Event}s
 * fired through the Quarkus runtime. Lets application beans listen via
 * {@code @Observes TaskStartedEvent} etc. without depending directly on the
 * framework's event types.
 *
 * <p>Reads no state from {@code WorkflowRunStore} inside event handlers
 * (event-payload-sufficiency principle). Small in-memory maps track
 * cross-event correlations (task name, step name, per-run agent name) so
 * the legacy CDI payload shapes stay backward-compatible.
 */
@ApplicationScoped
public class CdiEventBridge implements AgenticanEventListener {

    @Inject Instance<Agentican> agentican;

    private final ConcurrentHashMap<String, String> toolCallIds = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> taskNames = new ConcurrentHashMap<>();      // taskId -> taskName
    private final ConcurrentHashMap<String, ParentRef> parentRefs = new ConcurrentHashMap<>();  // taskId -> parent linkage
    private final ConcurrentHashMap<String, String> stepNames = new ConcurrentHashMap<>();      // stepId -> stepName
    private final ConcurrentHashMap<String, RunCtx> runCtx = new ConcurrentHashMap<>();         // runId  -> agent + stepId
    private final ConcurrentHashMap<String, String> turnToRun = new ConcurrentHashMap<>();      // turnId -> runId
    private final ConcurrentHashMap<String, Integer> turnIndices = new ConcurrentHashMap<>();   // turnId -> turn index
    private final ConcurrentHashMap<String, String> toolToTurn = new ConcurrentHashMap<>();     // toolCallId -> turnId

    private record ParentRef(String parentTaskId, String parentStepId, int iterationIndex) { }
    private record RunCtx(String agentName, String stepId) { }

    @Inject Event<WfRunStartedEvent> planStartedEvents;
    @Inject Event<WfRunCompletedEvent> planCompletedEvents;
    @Inject Event<TaskStartedEvent> taskStartedEvents;
    @Inject Event<TaskCompletedEvent> taskCompletedEvents;
    @Inject Event<StepStartedEvent> stepStartedEvents;
    @Inject Event<StepCompletedEvent> stepCompletedEvents;
    @Inject Event<RunStartedEvent> runStartedEvents;
    @Inject Event<RunCompletedEvent> runCompletedEvents;
    @Inject Event<TurnStartedEvent> turnStartedEvents;
    @Inject Event<TurnCompletedEvent> turnCompletedEvents;
    @Inject Event<MessageSentEvent> messageSentEvents;
    @Inject Event<ResponseReceivedEvent> responseReceivedEvents;
    @Inject Event<ToolCallStartedEvent> toolCallStartedEvents;
    @Inject Event<ToolCallCompletedEvent> toolCallCompletedEvents;
    @Inject Event<HitlCheckpointEvent> hitlCheckpointEvents;
    @Inject Event<IterationStartedEvent> iterationStartedEvents;
    @Inject Event<IterationCompletedEvent> iterationCompletedEvents;
    @Inject Event<TaskResumedEvent> taskResumedEvents;
    @Inject Event<TaskReapedEvent> taskReapedEvents;

    @Override
    public void on(AgenticanEvent event) {

        switch (event) {

            case PlanStarted   e -> planStartedEvents.fire(new WfRunStartedEvent(e.taskId(), taskNames.get(e.taskId())));
            case PlanCompleted e -> planCompletedEvents.fire(new WfRunCompletedEvent(e.taskId(), resolvePlanName(e.planId()), e.planId()));

            case TaskStarted   e -> onTaskStarted(e);
            case TaskCompleted e -> onTaskCompleted(e);

            case StepStarted   e -> onStepStarted(e);
            case StepCompleted e -> onStepCompleted(e);

            case RunStarted   e -> onRunStarted(e);
            case RunCompleted e -> onRunCompleted(e);

            case TurnStarted   e -> onTurnStarted(e);
            case TurnCompleted e -> onTurnCompleted(e);

            case MessageSent      e -> onMessageSent(e);
            case ResponseReceived e -> onResponseReceived(e);

            case ToolCallStarted   e -> onToolCallStarted(e);
            case ToolCallCompleted e -> onToolCallCompleted(e);

            case HitlNotified e -> onHitlNotified(e);

            case TaskResumed e -> taskResumedEvents.fire(new TaskResumedEvent(e.taskId()));
            case TaskReaped  e -> taskReapedEvents.fire(new TaskReapedEvent(e.taskId(), e.reason()));

            default -> { /* events with no CDI mapping (HitlResponded, BranchPathChosen, etc.) */ }
        }
    }

    private void onTaskStarted(TaskStarted event) {

        taskNames.put(event.taskId(), event.taskName());

        taskStartedEvents.fire(new TaskStartedEvent(event.taskId(), event.taskName(), event.parentTaskId(), null));

        if (event.parentTaskId() != null && event.parentStepId() != null) {

            parentRefs.put(event.taskId(),
                    new ParentRef(event.parentTaskId(), event.parentStepId(), event.iterationIndex()));

            iterationStartedEvents.fire(new IterationStartedEvent(
                    event.taskId(),
                    event.parentStepId(),
                    event.parentTaskId(),
                    event.taskName(),
                    event.iterationIndex()));
        }
    }

    private void onTaskCompleted(TaskCompleted event) {

        var taskName = taskNames.remove(event.taskId());

        taskCompletedEvents.fire(new TaskCompletedEvent(event.taskId(), taskName, event.status(), null));

        var parent = parentRefs.remove(event.taskId());

        if (parent != null) {

            iterationCompletedEvents.fire(new IterationCompletedEvent(
                    event.taskId(),
                    parent.parentStepId(),
                    parent.parentTaskId(),
                    event.status()));
        }
    }

    private void onStepStarted(StepStarted event) {

        stepNames.put(event.stepId(), event.stepName());

        stepStartedEvents.fire(new StepStartedEvent(event.stepId(), event.taskId(), event.stepName()));
    }

    private void onStepCompleted(StepCompleted event) {

        stepCompletedEvents.fire(new StepCompletedEvent(
                event.stepId(), event.taskId(), event.stepName(), event.status()));

        stepNames.remove(event.stepId());
    }

    private void onRunStarted(RunStarted event) {

        runCtx.put(event.runId(), new RunCtx(event.agentName(), event.stepId()));

        // runIndex is not a framework-level concept — agent steps may have multiple
        // runs across resume cycles, but the framework doesn't number them. CDI
        // observers that want ordering should rely on event arrival order on the bus.
        runStartedEvents.fire(new RunStartedEvent(
                event.runId(), event.stepId(), event.agentName(), 0, event.taskId()));
    }

    private void onRunCompleted(RunCompleted event) {

        var ctx = runCtx.remove(event.runId());

        runCompletedEvents.fire(new RunCompletedEvent(
                event.runId(),
                event.stepId() != null ? event.stepId() : (ctx != null ? ctx.stepId() : null),
                ctx != null ? ctx.agentName() : null,
                0,                          // see onRunStarted — runIndex isn't framework-tracked
                event.taskId()));
    }

    private void onTurnStarted(TurnStarted event) {

        turnToRun.put(event.turnId(), event.runId());
        turnIndices.put(event.turnId(), event.index());

        var ctx = runCtx.get(event.runId());
        var agentName = ctx != null ? ctx.agentName() : null;

        turnStartedEvents.fire(new TurnStartedEvent(
                event.turnId(), event.runId(), agentName, event.index(), event.taskId()));
    }

    private void onTurnCompleted(TurnCompleted event) {

        var runId = turnToRun.remove(event.turnId());
        turnIndices.remove(event.turnId());
        var ctx = runId != null ? runCtx.get(runId) : null;
        var agentName = ctx != null ? ctx.agentName() : null;

        turnCompletedEvents.fire(new TurnCompletedEvent(
                event.turnId(), runId, agentName, event.index(), event.taskId()));
    }

    private void onMessageSent(MessageSent event) {

        var runId = turnToRun.get(event.turnId());
        var ctx = runId != null ? runCtx.get(runId) : null;
        var agentName = ctx != null ? ctx.agentName() : null;
        var turnIndex = turnIndices.getOrDefault(event.turnId(), 0);

        // messageId is not generated by the framework — turnId uniquely identifies the
        // message within its turn since at most one MessageSent fires per turn.
        messageSentEvents.fire(new MessageSentEvent(
                null,
                event.turnId(), agentName, turnIndex, event.taskId()));
    }

    private void onResponseReceived(ResponseReceived event) {

        var runId = turnToRun.get(event.turnId());
        var ctx = runId != null ? runCtx.get(runId) : null;
        var agentName = ctx != null ? ctx.agentName() : null;
        var turnIndex = turnIndices.getOrDefault(event.turnId(), 0);

        var response = event.response();

        var toolCallCount = response != null && response.toolCalls() != null ? response.toolCalls().size() : 0;
        var inputTokens   = response != null ? response.inputTokens()  : 0;
        var outputTokens  = response != null ? response.outputTokens() : 0;
        var stopReason    = response != null ? response.stopReason()   : null;

        // responseId is not generated by the framework — turnId uniquely identifies
        // the response within its turn (one LLM round-trip per turn).
        responseReceivedEvents.fire(new ResponseReceivedEvent(
                null,
                event.turnId(), agentName, turnIndex, stopReason,
                inputTokens, outputTokens, toolCallCount, event.taskId()));
    }

    private void onToolCallStarted(ToolCallStarted event) {

        var hexId = Ids.generate();
        toolCallIds.put(event.toolCall().id(), hexId);
        toolToTurn.put(event.toolCall().id(), event.turnId());

        toolCallStartedEvents.fire(new ToolCallStartedEvent(
                hexId, event.turnId(), event.toolCall().name(), event.taskId()));
    }

    private void onToolCallCompleted(ToolCallCompleted event) {

        var result = event.toolResult();

        var hexId = toolCallIds.remove(result.toolCallId());
        if (hexId == null) hexId = Ids.generate();

        var turnId = toolToTurn.remove(result.toolCallId());
        if (turnId == null) turnId = event.turnId();

        toolCallCompletedEvents.fire(new ToolCallCompletedEvent(
                hexId, turnId, result.toolName(), result.isError(), event.taskId()));
    }

    private void onHitlNotified(HitlNotified event) {

        var stepName = stepNames.get(event.stepId());

        hitlCheckpointEvents.fire(new HitlCheckpointEvent(
                event.taskId(), event.stepId(), stepName, event.checkpoint()));
    }

    private String resolvePlanName(String planId) {

        if (!agentican.isResolvable()) return null;
        var plan = agentican.get().registry().workflows().byId(planId);
        return plan != null ? plan.name() : null;
    }
}
