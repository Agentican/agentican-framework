package ai.agentican.quarkus.metrics;

import ai.agentican.framework.event.AgenticanEvent;
import ai.agentican.framework.event.AgenticanEventListener;
import ai.agentican.framework.event.ResponseReceived;
import ai.agentican.framework.event.RunCompleted;
import ai.agentican.framework.event.RunStarted;
import ai.agentican.framework.event.StepCompleted;
import ai.agentican.framework.event.StepStarted;
import ai.agentican.framework.event.ToolCallCompleted;
import ai.agentican.framework.event.ToolCallStarted;
import ai.agentican.framework.event.TurnAbandoned;
import ai.agentican.framework.event.TurnCompleted;
import ai.agentican.framework.event.TurnStarted;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Records Micrometer metrics for agent activity. Subscribes to the
 * {@link AgenticanEvent} bus — no store reads inside event handlers (the
 * event-payload-sufficiency principle).
 *
 * <p>One field the event records do not yet carry is agent name / step name
 * at the moment a turn fires. The listener tracks {@code StepStarted} and
 * {@code RunStarted} into small maps so {@code ResponseReceived} /
 * {@code RunCompleted} can label metrics by agent + step. Cleared on the
 * corresponding completion events so memory is bounded by in-flight work.
 * (A future enrichment of the {@code AgentLoopHost} SPI could thread these
 * fields through and make the state tracking unnecessary.)
 */
public class MeteredTurnListener implements AgenticanEventListener {

    private final MeterRegistry registry;

    private final ConcurrentHashMap<String, Timer.Sample> toolTimers = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> stepNames = new ConcurrentHashMap<>();        // stepId -> stepName
    private final ConcurrentHashMap<String, RunCtx> runCtx = new ConcurrentHashMap<>();           // runId  -> agentName + stepId
    private final ConcurrentHashMap<String, String> turnToRun = new ConcurrentHashMap<>();        // turnId -> runId

    private record RunCtx(String agentName, String stepId) { }

    public MeteredTurnListener(MeterRegistry registry) {

        this.registry = registry;
    }

    @Override
    public void on(AgenticanEvent event) {

        switch (event) {

            case StepStarted   e -> stepNames.put(e.stepId(), e.stepName() != null ? e.stepName() : "unknown");
            case StepCompleted e -> stepNames.remove(e.stepId());

            case RunStarted   e -> runCtx.put(e.runId(),
                    new RunCtx(e.agentName() != null ? e.agentName() : "unknown", e.stepId()));
            case RunCompleted e -> onRunCompleted(e);

            case TurnStarted   e -> turnToRun.put(e.turnId(), e.runId());
            case TurnCompleted e -> turnToRun.remove(e.turnId());
            case TurnAbandoned e -> turnToRun.remove(e.turnId());

            case ResponseReceived  e -> onResponseReceived(e);

            case ToolCallStarted   e -> toolTimers.put(e.toolCall().id(), Timer.start(registry));
            case ToolCallCompleted e -> onToolCallCompleted(e);

            default -> { /* events with no metric mapping */ }
        }
    }

    private void onRunCompleted(RunCompleted event) {

        var ctx = runCtx.remove(event.runId());
        var agentName = ctx != null ? ctx.agentName() : "unknown";

        registry.counter("agentican.agent.runs",
                "agent",  agentName,
                "status", event.status().name()).increment();
    }

    private void onResponseReceived(ResponseReceived event) {

        var runId     = turnToRun.get(event.turnId());
        var ctx       = runId != null ? runCtx.get(runId) : null;
        var agentName = ctx != null ? ctx.agentName() : "unknown";
        var stepName  = ctx != null ? stepNames.getOrDefault(ctx.stepId(), "unknown") : "unknown";

        var response = event.response();

        registry.counter("agentican.agent.turns",
                "agent",       agentName,
                "step",        stepName,
                "stop_reason", response.stopReason().name()).increment();

        registry.counter("agentican.agent.turns.tokens.input",  "agent", agentName).increment(response.inputTokens());
        registry.counter("agentican.agent.turns.tokens.output", "agent", agentName).increment(response.outputTokens());
    }

    private void onToolCallCompleted(ToolCallCompleted event) {

        var result = event.toolResult();
        var toolName = result.toolName();

        var sample = toolTimers.remove(result.toolCallId());

        if (sample != null) sample.stop(registry.timer("agentican.tool.duration", "tool", toolName));

        registry.counter("agentican.tool.calls", "tool", toolName).increment();

        if (result.isError())
            registry.counter("agentican.tool.errors", "tool", toolName).increment();
    }
}
