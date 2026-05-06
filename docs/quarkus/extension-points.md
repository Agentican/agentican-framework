# Extension Points

The framework exposes four extension point interfaces. The Quarkus integration discovers
and composes all implementations via `Instance<T>.stream()` — multiple modules can provide
the same type and they stack correctly.

## WorkflowRunListener

Observes the full execution hierarchy: step → run → turn. Called synchronously on the
executing thread so implementations can maintain thread-local state (e.g. OTel spans).

```java
public interface WorkflowRunListener {
    // Task level
    default void onTaskStarted(String taskId, String taskName) {}
    default void onTaskCompleted(String taskId, String taskName, WorkflowRunStatus status) {}
    // Step level
    default void onStepStarted(String taskId, String stepName) {}
    default void onStepCompleted(String taskId, String stepName, WorkflowRunStatus status) {}
    // HITL
    default void onHitlCheckpoint(String taskId, String stepName, HitlCheckpoint checkpoint) {}
    // Run level
    default void onRunStarted(String agentName, String stepName) {}
    default void onRunCompleted(String agentName, String stepName, AgentResult result) {}
    // Turn level
    default void onTurnStarted(String agentName, String stepName, int turn) {}
    default void onTurnCompleted(String agentName, String stepName, int turn, LlmResponse response) {}
    // Token streaming
    default void onToken(String agentName, String stepName, int turn, String token) {}
}
```

Override only the levels you care about. In Quarkus, produce as a CDI bean:

```java
@Produces @ApplicationScoped
public WorkflowRunListener myListener() {
    return new WorkflowRunListener() {
        @Override
        public void onTurnCompleted(String agent, String step, int turn, LlmResponse r) {
            log.info("Agent {} turn {} used {} tokens", agent, turn, r.outputTokens());
        }
    };
}
```

**Used by:** `quarkus-otel` (step + run + turn spans), `quarkus-metrics` (run + turn counters)

## LlmClientDecorator

Wraps every LLM client built from config. The decorator receives the full `LlmConfig`
(name, model, provider) for tagging.

```java
@FunctionalInterface
public interface LlmClientDecorator {
    LlmClient decorate(LlmConfig config, LlmClient client);
}
```

```java
@Produces @ApplicationScoped
public LlmClientDecorator loggingDecorator() {
    return (config, client) -> request -> {
        log.info("Calling {} ({})", config.name(), config.model());
        return client.send(request);
    };
}
```

**Used by:** `quarkus-metrics` (token counters, latency timers), `quarkus-otel` (LLM call spans)

## WorkflowRunDecorator

Wraps the workflow-run `Supplier` before it's submitted to the virtual thread executor.
Used for context propagation across thread boundaries (HTTP thread → virtual thread).

```java
public interface WorkflowRunDecorator {
    <T> Supplier<T> decorate(Supplier<T> task);
    default WorkflowRunDecorator snapshot() { return this; }
}
```

`snapshot()` is invoked when the decorator must be re-applied at a sub-task boundary
(loops, branches) — most decorators just `return this;` (the default), but OTel uses it
to capture the current `Context` for restoration on the child run's thread.

**Used by:** `quarkus-otel` (propagates OTel context from HTTP thread to virtual thread)

## Custom executor

Provide a managed `ExecutorService` for task execution:

```java
@Produces
public ExecutorService agenticanExecutor() {
    return Executors.newVirtualThreadPerTaskExecutor();
}
```

If not provided, the framework creates its own. Externally-provided executors are NOT
shut down by `Agentican.close()` — the provider manages their lifecycle.

## Composition

When both `quarkus-metrics` and `quarkus-otel` are on the classpath, the producer
composes all beans of the same type into a chain:

```
LlmClientDecorator:    metrics wraps → otel wraps → raw client
WorkflowRunDecorator:  composed via snapshot() chain
WorkflowRunListener:   all listeners called for each event
```

No configuration needed — it's automatic via `Instance<T>.stream()`.
