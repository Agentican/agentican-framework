# Observability

Agentican emits lifecycle events at every level of execution — from task start/complete down to individual tool calls and LLM tokens. These events power metrics, tracing, dashboards, and custom integrations.

## TaskListener

The primary extension point for observability. Implement any subset of methods — all have no-op defaults:

```java
public interface TaskListener {

    // Planning
    default void onPlanStarted(String taskId) {}
    default void onPlanCompleted(String taskId, String planId) {}

    // Task
    default void onTaskStarted(String taskId) {}
    default void onTaskCompleted(String taskId, TaskStatus status) {}

    // Step
    default void onStepStarted(String taskId, String stepId) {}
    default void onStepCompleted(String taskId, String stepId) {}

    // Run (agent execution attempt within a step)
    default void onRunStarted(String taskId, String runId) {}
    default void onRunCompleted(String taskId, String runId, AgentStatus status) {}

    // Turn (one LLM round-trip within a run)
    default void onTurnStarted(String taskId, String turnId) {}
    default void onTurnCompleted(String taskId, String turnId) {}

    // Message / Response (within a turn)
    default void onMessageSent(String taskId, String turnId) {}
    default void onResponseReceived(String taskId, String turnId, StopReason stopReason) {}

    // Tool calls (within a turn)
    default void onToolCallStarted(String taskId, String toolCallId) {}
    default void onToolCallCompleted(String taskId, String toolCallId) {}

    // HITL
    default void onHitlNotified(String taskId, String hitlId, HitlCheckpoint.Type type) {}
    default void onHitlResponded(String taskId, String hitlId, boolean approved) {}

    // Streaming
    default void onToken(String taskId, String turnId, String token) {}
}
```

### Event Signatures

Every event carries `taskId` plus the ID of the object it describes. This keeps signatures lightweight — if you need more detail (agent name, step name, token usage), look it up in the `TaskStateStore`.

```java
onStepStarted(taskId, stepId)        // which task, which step
onRunStarted(taskId, runId)          // which task, which run
onToolCallStarted(taskId, toolCallId) // which task, which tool call
```

## Event Hierarchy

Events follow the execution hierarchy. For a single-step task with one tool call, the event sequence is:

```
onPlanStarted(taskId)
onPlanCompleted(taskId, planId)
onTaskStarted(taskId)
  onStepStarted(taskId, stepId)
    onRunStarted(taskId, runId)
      onTurnStarted(taskId, turnId)
        onMessageSent(taskId, turnId)
        onToken(taskId, turnId, "The")        ← streaming tokens
        onToken(taskId, turnId, " answer")
        onResponseReceived(taskId, turnId, TOOL_USE)
        onToolCallStarted(taskId, toolCallId)
        onToolCallCompleted(taskId, toolCallId)
      onTurnCompleted(taskId, turnId)
      onTurnStarted(taskId, turnId2)          ← next turn with tool results
        onMessageSent(taskId, turnId2)
        onResponseReceived(taskId, turnId2, END_TURN)
      onTurnCompleted(taskId, turnId2)
    onRunCompleted(taskId, runId, COMPLETED)
  onStepCompleted(taskId, stepId)
onTaskCompleted(taskId, COMPLETED)
```

## Enums in Events

### StopReason (in `onResponseReceived`)

| Value | Meaning |
|-------|---------|
| `END_TURN` | LLM finished with a text response |
| `TOOL_USE` | LLM is requesting tool calls |
| `MAX_TOKENS` | LLM hit the token limit |

### AgentStatus (in `onRunCompleted`)

| Value | Meaning |
|-------|---------|
| `COMPLETED` | Agent finished normally |
| `CANCELLED` | Task was cancelled |
| `TIMED_OUT` | Per-step timeout exceeded |
| `MAX_TURNS` | Agent hit the max turns limit |
| `SUSPENDED` | Agent is waiting for HITL response |

## How Events Are Emitted

Events are a side effect of `TaskStateStore` mutations. The framework wraps your store with `TaskStateStoreNotifying`, a decorator that:

1. Delegates the mutation to the underlying store (state is persisted first)
2. Fires the corresponding `TaskListener` event

```
TaskRunner calls → taskStateStore.stepStarted(taskId, stepId, stepName)
                       ↓
              TaskStateStoreNotifying
                  ├── delegate.stepStarted(...)   ← state persisted
                  └── listener.onStepStarted(taskId, stepId)  ← event fired
```

This means events are guaranteed to fire after state is committed. If you query the store inside an event handler, the data will be there.

## Implementing a Listener

### Simple Logger

```java
public class LoggingListener implements TaskListener {

    @Override
    public void onTaskStarted(String taskId) {
        System.out.println("Task started: " + taskId);
    }

    @Override
    public void onTaskCompleted(String taskId, TaskStatus status) {
        System.out.println("Task " + taskId + " completed: " + status);
    }

    @Override
    public void onToken(String taskId, String turnId, String token) {
        System.out.print(token);  // stream tokens to console
    }
}
```

### Enriching Events from the Store

Event signatures are intentionally minimal. To get richer data, query the `TaskStateStore`:

```java
public class DetailedListener implements TaskListener {

    private final TaskStateStore store;

    @Override
    public void onStepCompleted(String taskId, String stepId) {

        var taskLog = store.load(taskId);
        var step = taskLog.findStepById(stepId);

        System.out.printf("Step '%s' completed — %d tokens used%n",
                step.stepName(), step.inputTokens() + step.outputTokens());
    }
}
```

### Registration

Register via the Agentican builder:

```java
Agentican.builder()
        .stepListener(myListener)
        .build();
```

Multiple listeners can be composed in a Quarkus/CDI environment using `Instance<TaskListener>` — the `AgenticanProducer` creates a composite that fans out to all registered beans.

## TaskDecorator

`TaskDecorator` wraps the `Supplier` submitted to `CompletableFuture.supplyAsync()` for each task execution. Its primary use is propagating context (e.g., OTel trace context) from the caller thread into the task's virtual thread.

```java
@FunctionalInterface
public interface TaskDecorator {

    <T> Supplier<T> decorate(Supplier<T> task);

    default TaskDecorator snapshot() { return this; }
}
```

### Context Propagation

The `snapshot()` method captures the current context at the point it's called, returning a decorator that will restore that context on any thread. This is critical for step dispatch, where each step runs on its own virtual thread:

```
Main thread (has OTel context)
  └── TaskDecorator.snapshot()  ← captures context
        └── step virtual thread
              └── decorate(stepWork)  ← restores captured context
```

Without `snapshot()`, steps dispatched to new threads would lose the parent trace context.

Register via the builder:

```java
Agentican.builder()
        .taskDecorator(myDecorator)
        .build();
```

## Threading Model

Events fire synchronously on the thread that triggered the store mutation:

- **Task-level events** (`onTaskStarted`, `onTaskCompleted`) fire on the task's virtual thread
- **Step-level events** fire on the step's virtual thread (one per step for parallel steps)
- **Run/turn/tool events** fire on the agent runner's thread (same as the step thread)
- **`onToken`** fires on the LLM streaming thread

Keep event handlers fast. If you need to do heavy work (send to a queue, write to a database), dispatch it asynchronously.

## Next Steps

- [Execution State](execution.md) — the data model behind events
- [Configuration](configuration.md) — registering listeners and decorators
- [Human in the Loop](hitl.md) — HITL events
