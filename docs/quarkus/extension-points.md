# Extension Points

The framework exposes four extension point interfaces. The Quarkus integration discovers
and composes all implementations via `Instance<T>.stream()` — multiple modules can provide
the same type and they stack correctly.

## StepListener

Observes the full execution hierarchy: step → run → turn. Called synchronously on the
executing thread so implementations can maintain thread-local state (e.g. OTel spans).

```java
public interface StepListener {
    // Task level
    default void onTaskStarted(String taskId, String taskName) {}
    default void onTaskCompleted(String taskId, String taskName, TaskStatus status) {}
    // Step level
    default void onStepStarted(String taskId, String stepName) {}
    default void onStepCompleted(String taskId, String stepName, TaskStatus status) {}
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
public StepListener myListener() {
    return new StepListener() {
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

## ToolkitDecorator

Wraps every toolkit (MCP, Composio, custom, built-ins). The decorator receives the
toolkit slug for tagging.

```java
@FunctionalInterface
public interface ToolkitDecorator {
    Toolkit decorate(String slug, Toolkit toolkit);
}
```

**Used by:** `quarkus-metrics` (tool call counters), `quarkus-otel` (tool call spans)

## TaskExecutionDecorator

Wraps the task `Supplier` before it's submitted to the virtual thread executor. Used for
context propagation across thread boundaries.

```java
@FunctionalInterface
public interface TaskExecutionDecorator {
    <T> Supplier<T> decorate(Supplier<T> task);
}
```

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
LlmClientDecorator: metrics wraps → otel wraps → raw client
ToolkitDecorator:   metrics wraps → otel wraps → raw toolkit
StepListener:       all listeners called for each event
```

No configuration needed — it's automatic via `Instance<T>.stream()`.
