package ai.agentican.quarkus.metrics;

import ai.agentican.framework.TaskListener;
import ai.agentican.framework.agent.AgentStatus;
import ai.agentican.framework.llm.StopReason;
import ai.agentican.framework.state.TaskStateStore;
import ai.agentican.framework.tools.ToolResult;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import java.util.concurrent.ConcurrentHashMap;

public class MeteredTurnListener implements TaskListener {

    private final MeterRegistry registry;
    private final TaskStateStore taskStateStore;
    private final ConcurrentHashMap<String, Timer.Sample> toolTimers = new ConcurrentHashMap<>();

    public MeteredTurnListener(MeterRegistry registry, TaskStateStore taskStateStore) {

        this.registry = registry;
        this.taskStateStore = taskStateStore;
    }

    @Override
    public void onRunCompleted(String taskId, String runId, AgentStatus status) {

        var run = taskStateStore.load(taskId).findRunById(runId);

        var agentName = run != null && run.agentName() != null ? run.agentName() : "unknown";

        registry.counter("agentican.agent.runs", "agent", agentName, "status", status.name()).increment();
    }

    @Override
    public void onResponseReceived(String taskId, String turnId, StopReason stopReason) {

        var taskLog = taskStateStore.load(taskId);
        var turnLog = taskLog != null ? taskLog.findTurnById(turnId) : null;

        var response = turnLog != null ? turnLog.response() : null;

        var agentName = "unknown";
        var stepName = "unknown";

        if (taskLog != null) {

            outer:
            for (var step : taskLog.steps().values()) {

                for (var run : step.runs()) {

                    for (var turn : run.turns()) {

                        if (turnId.equals(turn.id())) {

                            agentName = run.agentName() != null ? run.agentName() : "unknown";
                            stepName = step.stepName();

                            break outer;
                        }
                    }
                }
            }
        }

        registry.counter("agentican.agent.turns", "agent", agentName, "step", stepName,
                "stop_reason", stopReason.name()).increment();

        if (response != null) {
            registry.counter("agentican.agent.turns.tokens.input", "agent", agentName)
                    .increment(response.inputTokens());

            registry.counter("agentican.agent.turns.tokens.output", "agent", agentName)
                    .increment(response.outputTokens());
        }
    }

    @Override
    public void onToolCallStarted(String taskId, String toolCallId) {

        toolTimers.put(toolCallId, Timer.start(registry));
    }

    @Override
    public void onToolCallCompleted(String taskId, String toolCallId) {

        var sample = toolTimers.remove(toolCallId);

        var taskLog = taskStateStore.load(taskId);

        ToolResult toolResult = null;

        String toolName = "unknown";

        if (taskLog != null) {

            outer:

            for (var step : taskLog.steps().values()) {

                for (var run : step.runs()) {

                    for (var turn : run.turns()) {

                        for (var tr : turn.toolResults()) {

                            if (toolCallId.equals(tr.toolCallId())) {

                                toolResult = tr;
                                toolName = tr.toolName();

                                break outer;
                            }
                        }
                    }
                }
            }
        }

        if (sample != null)
            sample.stop(registry.timer("agentican.tool.duration", "tool", toolName));

        registry.counter("agentican.tool.calls", "tool", toolName).increment();

        if (toolResult != null && toolResult.isError())
            registry.counter("agentican.tool.errors", "tool", toolName).increment();
    }
}
