# Observability

Agentican emits lifecycle events at every level of execution — from task start/complete down to individual tool calls and LLM tokens. These events power metrics, tracing, dashboards, and custom integrations.

## The Event Bus

`AgenticanEventBus` is a synchronous publish/subscribe bus. The framework publishes a sealed `AgenticanEvent` for every significant lifecycle moment; persistence, observability, and custom integrations all hang off the bus as listeners — including the framework's own `WorkflowRunStorePersister` (state) and the optional Quarkus modules for metrics, OTel, and CDI bridging.

```java
public sealed interface AgenticanEvent
        permits PlanStarted, PlanCompleted,
                TaskStarted, TaskCompleted, TaskReaped, TaskResumed,
                StepStarted, StepCompleted, StepTokenUsageAggregated, StepResumed,
                BranchPathChosen,
                RunStarted, RunCompleted, RunResumed,
                TurnStarted, TurnCompleted, TurnAbandoned, TurnResumed, TokenStreamed,
                MessageSent, ResponseReceived,
                ToolCallStarted, ToolCallCompleted,
                HitlNotified, HitlResponded {

    String taskId();
}
```

### Event-payload sufficiency

Every event carries everything a reasonable listener could need — ids, statuses, outputs, token usage, and the underlying domain objects (`LlmRequest`, `LlmResponse`, `ToolCall`, `ToolResult`, `HitlCheckpoint`, `WorkflowDefinition`, etc.). Listeners should **not** call `WorkflowRunStore.load(...)` from inside an event handler to compensate for thin payloads. If you find yourself wanting more data, the right fix is to enrich the event at the publish site, not to read state.

This matters because listeners are called on the publishing thread; an extra store round-trip inside every event handler adds latency to the hot path and re-reads data the framework already had in hand. (Batch / recovery paths that aren't on the bus's hot path — e.g. `AgenticanRecovery.reingestCompletedSteps` — do read from the store; that's by design.)

### One bus, two runtimes

The bus is process-scoped: `Agentican.eventBus()` returns the single instance every listener is subscribed to. The same bus handles events from in-process work AND from Temporal-driven workflows running on the same JVM. The Temporal integration ships a one-line forwarder listener on each workflow's private (sparsely-subscribed) bus that calls an `AgenticanActivity.publish(event)` activity; on the activity worker side, that activity simply publishes the event onto the main bus, where the same listeners process it. From a listener's perspective, Temporal is invisible — events just arrive. See [Temporal Integration](temporal.md#event-flow-under-temporal) for the topology.

## Implementing a Listener

`AgenticanEventListener` is a single-method functional interface. Implementations pattern-match on the sealed hierarchy:

```java
public interface AgenticanEventListener {

    void on(AgenticanEvent event);
}
```

A concrete listener:

```java
public class LoggingListener implements AgenticanEventListener {

    @Override
    public void on(AgenticanEvent event) {

        switch (event) {

            case TaskStarted   e -> System.out.println("Task started: " + e.taskName());
            case TaskCompleted e -> System.out.println("Task " + e.taskId() + ": " + e.status());

            case TokenStreamed t -> System.out.print(t.token());      // stream to console
            case ResponseReceived r -> {
                var tokens = r.response().inputTokens() + r.response().outputTokens();
                System.out.printf("LLM round-trip used %d tokens%n", tokens);
            }

            default -> { /* ignore everything else */ }
        }
    }
}
```

The single-method shape gives compile-time exhaustiveness via `switch` on the sealed type — adding a new event in a later framework version produces a compile error in listeners that need to handle it, instead of being silently dropped by a typed `onSomething()` method that didn't exist yet.

## subscribe vs subscribeFirst

There are two listener slots on the bus:

| Method | Slots | Exception policy | Used by |
|---|---|---|---|
| `subscribeFirst(...)` | One — the persister tier | Propagates — persistence failure must reach the caller | `WorkflowRunStorePersister`. Under Temporal, the same persister runs on the activity worker side after a one-line `ForwarderListener` ships the event over the activity boundary. |
| `subscribe(...)` | Many — observer tier, FIFO | Logged and swallowed — one listener cannot break another | Metrics, OTel, CDI bridge, custom logging, REST event bus |

The persister runs first so that when an observer's handler reads from the store (e.g. a metrics emitter querying token counts), state is already committed. Observer listeners then run in registration order.

## Event Hierarchy

For a single-step task with one tool call, the event sequence is:

```
PlanStarted(taskId)
PlanCompleted(taskId, planId)
TaskStarted(taskId, taskName, plan, params, parentTaskId, parentStepId, iterationIndex,
            runtime, temporalWorkflowId)
  StepStarted(taskId, stepId, stepName)
    RunStarted(taskId, stepId, runId, agentName)
      TurnStarted(taskId, runId, turnId, index)
        MessageSent(taskId, turnId, request)
        TokenStreamed(taskId, turnId, "The")            ← streaming chunks
        TokenStreamed(taskId, turnId, " answer")
        ResponseReceived(taskId, turnId, response)
        ToolCallStarted(taskId, turnId, toolCall)
        ToolCallCompleted(taskId, turnId, toolResult)
      TurnCompleted(taskId, turnId, index, tokenUsage)
      TurnStarted(taskId, runId, turnId2, 1)            ← next turn with tool results
        MessageSent(taskId, turnId2, request)
        ResponseReceived(taskId, turnId2, response)
      TurnCompleted(taskId, turnId2, 1, tokenUsage)
    RunCompleted(taskId, stepId, runId, status, tokenUsage)
  StepCompleted(taskId, stepId, stepName, status, output)
TaskCompleted(taskId, status)
```

`TaskStarted.runtime` is `IN_PROCESS` for the framework's in-process executor and `TEMPORAL` for tasks driven by a Temporal workflow; `temporalWorkflowId` is non-null when `runtime == TEMPORAL`. See [Execution State](execution.md#runtime-owner) for how the framework uses these fields.

## Notable enums in event payloads

`ResponseReceived.response().stopReason()`:

| Value | Meaning |
|---|---|
| `END_TURN` | LLM finished with a text response |
| `TOOL_USE` | LLM is requesting tool calls |
| `MAX_TOKENS` | LLM hit the token limit |

`RunCompleted.status()` (`AgentStatus`):

| Value | Meaning |
|---|---|
| `COMPLETED` | Agent finished normally |
| `CANCELLED` | Task was cancelled |
| `TIMED_OUT` | Per-step timeout exceeded |
| `MAX_TURNS` | Agent hit the max turns limit |
| `SUSPENDED` | Agent is waiting for HITL response |

`TaskCompleted.status()` (`WorkflowRunStatus`): `COMPLETED`, `FAILED`, `CANCELLED`.

## Registration

Register via the Agentican builder:

```java
Agentican.builder()
        .addListener(myListener)       // an observer; can be called many times
        .eventBus(customBus)           // optional; supplies your own bus
        .build();
```

Under Quarkus, every CDI bean implementing `AgenticanEventListener` is auto-discovered and subscribed by `AgenticanProducer` — no manual wiring needed:

```java
@ApplicationScoped
public class MyListener implements AgenticanEventListener {

    @Override public void on(AgenticanEvent event) { /* ... */ }
}
```

### Late subscription

After `build()` returns, you can still subscribe more listeners against the live bus via the `eventBus()` accessor:

```java
var agentican = Agentican.builder().build();

agentican.eventBus().subscribe(extraListener);
```

This is useful for tools that wire themselves up post-construction (e.g. a Temporal worker that registers the same `Agentican.eventBus()` so events emitted from `AgenticanActivity.publish` reach the same listener set) or for tests that want to attach an assertion-collecting listener for one specific check.

The `eventBus()` instance is also the singleton that the Temporal integration ships events to — see [Temporal Integration](temporal.md#event-flow-under-temporal).

## WorkflowRunDecorator

`WorkflowRunDecorator` wraps the `Supplier` submitted to `CompletableFuture.supplyAsync()` for each task execution. Its primary use is propagating context (e.g. OTel trace context) from the caller thread into the task's virtual thread.

```java
@FunctionalInterface
public interface WorkflowRunDecorator {

    <T> Supplier<T> decorate(Supplier<T> task);

    default WorkflowRunDecorator snapshot() { return this; }
}
```

### Context propagation

The `snapshot()` method captures the current context at the point it's called, returning a decorator that will restore that context on any thread. This is critical for step dispatch, where each step runs on its own virtual thread:

```
Main thread (has OTel context)
  └── WorkflowRunDecorator.snapshot()  ← captures context
        └── step virtual thread
              └── decorate(stepWork)  ← restores captured context
```

Without `snapshot()`, steps dispatched to new threads would lose the parent trace context.

Register via the builder:

```java
Agentican.builder()
        .workflowRunDecorator(myDecorator)
        .build();
```

## Threading model

`AgenticanEventBus.publish(event)` dispatches synchronously on the publishing thread:

- **Task-level events** fire on the task's virtual thread
- **Step-level events** fire on the step's virtual thread (one per step for parallel steps)
- **Run/turn/tool events** fire on the agent runner's thread (typically the step thread)
- **`TokenStreamed`** fires on the LLM streaming thread

Keep observer handlers fast — they're on the critical path. If you need to do heavy work (send to a queue, write to a database asynchronously), dispatch it from the handler.

## Next steps

- [Execution State](execution.md) — the data model behind events
- [Configuration](configuration.md) — registering listeners and decorators
- [Human in the Loop](hitl.md) — HITL events
