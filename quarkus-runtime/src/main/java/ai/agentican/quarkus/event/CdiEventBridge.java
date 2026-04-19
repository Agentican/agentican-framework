package ai.agentican.quarkus.event;

import ai.agentican.framework.Agentican;
import ai.agentican.framework.TaskListener;
import ai.agentican.framework.agent.AgentStatus;
import ai.agentican.framework.hitl.HitlCheckpointType;
import ai.agentican.framework.llm.StopReason;
import ai.agentican.framework.state.StepLog;
import ai.agentican.framework.state.TaskStateStore;
import ai.agentican.framework.orchestration.execution.TaskStatus;
import ai.agentican.framework.util.Ids;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

@ApplicationScoped
public class CdiEventBridge implements TaskListener {

    @Inject TaskStateStore taskStateStore;

    @Inject Instance<Agentican> agentican;

    private final java.util.concurrent.ConcurrentHashMap<String, String> toolCallIds = new java.util.concurrent.ConcurrentHashMap<>();

    @Inject Event<PlanStartedEvent> planStartedEvents;
    @Inject Event<PlanCompletedEvent> planCompletedEvents;
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
    public void onPlanStarted(String taskId) {

        var taskLog = taskStateStore.load(taskId);
        var taskDescription = taskLog != null ? taskLog.taskName() : null;
        planStartedEvents.fire(new PlanStartedEvent(taskId, taskDescription));
    }

    @Override
    public void onPlanCompleted(String taskId, String planId) {

        var taskName = resolvePlanName(planId);
        planCompletedEvents.fire(new PlanCompletedEvent(taskId, taskName, planId));
    }

    private String resolvePlanName(String planId) {

        if (!agentican.isResolvable()) return null;
        var plan = agentican.get().registry().plans().getById(planId);
        return plan != null ? plan.name() : null;
    }

    @Override
    public void onTaskStarted(String taskId) {

        var taskLog = taskStateStore.load(taskId);
        var taskName = taskLog != null ? taskLog.taskName() : null;

        taskStartedEvents.fire(new TaskStartedEvent(taskId, taskName, null));

        if (taskLog != null && taskLog.parentTaskId() != null && taskLog.parentStepId() != null) {

            iterationStartedEvents.fire(new IterationStartedEvent(
                    taskId,
                    taskLog.parentStepId(),
                    taskLog.parentTaskId(),
                    taskName,
                    taskLog.iterationIndex()));
        }
    }

    @Override
    public void onTaskCompleted(String taskId, TaskStatus status) {

        var taskLog = taskStateStore.load(taskId);
        var taskName = taskLog != null ? taskLog.taskName() : null;

        taskCompletedEvents.fire(new TaskCompletedEvent(taskId, taskName, status, null));

        if (taskLog != null && taskLog.parentTaskId() != null && taskLog.parentStepId() != null) {

            iterationCompletedEvents.fire(new IterationCompletedEvent(
                    taskId,
                    taskLog.parentStepId(),
                    taskLog.parentTaskId(),
                    status));
        }
    }

    @Override
    public void onStepStarted(String taskId, String stepId) {

        var step = resolveStepById(taskId, stepId);
        var stepName = step != null ? step.stepName() : null;
        stepStartedEvents.fire(new StepStartedEvent(stepId, taskId, stepName));
    }

    @Override
    public void onStepCompleted(String taskId, String stepId) {

        var step = resolveStepById(taskId, stepId);
        var stepName = step != null ? step.stepName() : null;
        var status = step != null ? step.status() : null;
        stepCompletedEvents.fire(new StepCompletedEvent(stepId, taskId, stepName, status));
    }

    @Override
    public void onRunStarted(String taskId, String runId) {

        var taskLog = taskStateStore.load(taskId);
        var run = taskLog != null ? taskLog.findRunById(runId) : null;
        var agentName = run != null ? run.agentName() : null;
        var runIndex = run != null ? run.index() : 0;

        var stepId = resolveStepIdForRun(taskLog, runId);
        runStartedEvents.fire(new RunStartedEvent(runId, stepId, agentName, runIndex, taskId));
    }

    @Override
    public void onRunCompleted(String taskId, String runId, AgentStatus status) {

        var taskLog = taskStateStore.load(taskId);
        var run = taskLog != null ? taskLog.findRunById(runId) : null;
        var agentName = run != null ? run.agentName() : null;
        var runIndex = run != null ? run.index() : 0;

        var stepId = resolveStepIdForRun(taskLog, runId);
        runCompletedEvents.fire(new RunCompletedEvent(runId, stepId, agentName, runIndex, taskId));
    }

    @Override
    public void onTurnStarted(String taskId, String turnId) {

        var taskLog = taskStateStore.load(taskId);
        var turnLog = taskLog != null ? taskLog.findTurnById(turnId) : null;
        var turn = turnLog != null ? turnLog.index() : 0;

        var agentName = (String) null;
        var runId = (String) null;
        if (taskLog != null) {
            outer:
            for (var step : taskLog.steps().values()) {
                for (var run : step.runs()) {
                    for (var t : run.turns()) {
                        if (turnId.equals(t.id())) {
                            agentName = run.agentName();
                            runId = run.id();
                            break outer;
                        }
                    }
                }
            }
        }
        turnStartedEvents.fire(new TurnStartedEvent(turnId, runId, agentName, turn, taskId));
    }

    @Override
    public void onTurnCompleted(String taskId, String turnId) {

        var taskLog = taskStateStore.load(taskId);
        var turnLog = taskLog != null ? taskLog.findTurnById(turnId) : null;
        var turn = turnLog != null ? turnLog.index() : 0;

        var agentName = (String) null;
        var runId = (String) null;
        if (taskLog != null) {
            outer:
            for (var step : taskLog.steps().values()) {
                for (var run : step.runs()) {
                    for (var t : run.turns()) {
                        if (turnId.equals(t.id())) {
                            agentName = run.agentName();
                            runId = run.id();
                            break outer;
                        }
                    }
                }
            }
        }
        turnCompletedEvents.fire(new TurnCompletedEvent(turnId, runId, agentName, turn, taskId));
    }

    @Override
    public void onMessageSent(String taskId, String turnId) {

        var taskLog = taskStateStore.load(taskId);
        var turnLog = taskLog != null ? taskLog.findTurnById(turnId) : null;

        var agentName = (String) null;
        var turn = turnLog != null ? turnLog.index() : 0;
        if (taskLog != null) {
            outer:
            for (var step : taskLog.steps().values()) {
                for (var run : step.runs()) {
                    for (var t : run.turns()) {
                        if (turnId.equals(t.id())) {
                            agentName = run.agentName();
                            break outer;
                        }
                    }
                }
            }
        }
        messageSentEvents.fire(new MessageSentEvent(
                turnLog != null ? turnLog.messageId() : null,
                turnId, agentName, turn, taskId));
    }

    @Override
    public void onResponseReceived(String taskId, String turnId, StopReason stopReason) {

        var taskLog = taskStateStore.load(taskId);
        var turnLog = taskLog != null ? taskLog.findTurnById(turnId) : null;
        var response = turnLog != null ? turnLog.response() : null;

        var agentName = (String) null;
        var turn = turnLog != null ? turnLog.index() : 0;
        if (taskLog != null) {
            outer:
            for (var step : taskLog.steps().values()) {
                for (var run : step.runs()) {
                    for (var t : run.turns()) {
                        if (turnId.equals(t.id())) {
                            agentName = run.agentName();
                            break outer;
                        }
                    }
                }
            }
        }
        responseReceivedEvents.fire(new ResponseReceivedEvent(
                turnLog != null ? turnLog.responseId() : null,
                turnId, agentName, turn, stopReason,
                response != null ? response.inputTokens() : 0,
                response != null ? response.outputTokens() : 0,
                response != null && response.toolCalls() != null ? response.toolCalls().size() : 0,
                taskId));
    }

    @Override
    public void onToolCallStarted(String taskId, String toolCallId) {

        var hexId = Ids.generate();
        toolCallIds.put(toolCallId, hexId);

        var taskLog = taskStateStore.load(taskId);
        var toolName = resolveToolNameByCallId(taskLog, toolCallId);
        var turnId = resolveTurnIdForToolCall(taskLog, toolCallId);

        toolCallStartedEvents.fire(new ToolCallStartedEvent(hexId, turnId, toolName, taskId));
    }

    @Override
    public void onToolCallCompleted(String taskId, String toolCallId) {

        var hexId = toolCallIds.remove(toolCallId);
        if (hexId == null) hexId = Ids.generate();

        var taskLog = taskStateStore.load(taskId);
        var toolResult = resolveToolResultByCallId(taskLog, toolCallId);
        var toolName = toolResult != null ? toolResult.toolName() : "unknown";
        var isError = toolResult != null && toolResult.isError();
        var turnId = resolveTurnIdForToolCall(taskLog, toolCallId);

        toolCallCompletedEvents.fire(new ToolCallCompletedEvent(hexId, turnId, toolName, isError, taskId));
    }

    @Override
    public void onHitlNotified(String taskId, String hitlId, HitlCheckpointType type) {

        var taskLog = taskStateStore.load(taskId);
        String stepId = null;
        String stepName = null;
        ai.agentican.framework.hitl.HitlCheckpoint checkpoint = null;
        if (taskLog != null) {
            for (var step : taskLog.steps().values()) {
                if (step.checkpoint() != null && hitlId.equals(step.checkpoint().id())) {
                    stepId = step.id();
                    stepName = step.stepName();
                    checkpoint = step.checkpoint();
                    break;
                }
            }
        }
        hitlCheckpointEvents.fire(new HitlCheckpointEvent(taskId, stepId, stepName, checkpoint));
    }

    @Override
    public void onTaskResumed(String taskId) {

        taskResumedEvents.fire(new TaskResumedEvent(taskId));
    }

    @Override
    public void onTaskReaped(String taskId,
                             ai.agentican.framework.orchestration.execution.resume.ReapReason reason) {

        taskReapedEvents.fire(new TaskReapedEvent(taskId, reason));
    }

    private StepLog resolveStepById(String taskId, String stepId) {

        var taskLog = taskStateStore.load(taskId);
        return taskLog != null ? taskLog.findStepById(stepId) : null;
    }

    private static String resolveStepIdForRun(ai.agentican.framework.state.TaskLog taskLog, String runId) {

        if (taskLog == null) return null;

        for (var step : taskLog.steps().values()) {
            for (var run : step.runs()) {
                if (runId.equals(run.id())) return step.id();
            }
        }
        return null;
    }

    private static String resolveTurnIdForToolCall(ai.agentican.framework.state.TaskLog taskLog, String toolCallId) {

        if (taskLog == null) return null;

        for (var step : taskLog.steps().values()) {
            for (var run : step.runs()) {
                for (var turn : run.turns()) {
                    if (turn.response() != null && turn.response().toolCalls() != null) {
                        for (var tc : turn.response().toolCalls()) {
                            if (toolCallId.equals(tc.id())) return turn.id();
                        }
                    }
                    for (var tr : turn.toolResults()) {
                        if (toolCallId.equals(tr.toolCallId())) return turn.id();
                    }
                }
            }
        }
        return null;
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
}
