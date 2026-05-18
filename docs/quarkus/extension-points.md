# Extension Points

The framework exposes four extension point interfaces. The Quarkus integration discovers
and composes all implementations via `Instance<T>.stream()` — multiple modules can provide
the same type and they stack correctly.

## AgenticanEventListener

Subscribes to every event on the `AgenticanEventBus` — task, step, run, turn, message,
response, tool calls, HITL, token streams. One method, pattern-matched on the sealed
`AgenticanEvent` hierarchy. Called synchronously on the publishing thread, so
implementations can maintain thread-local state (e.g. OTel spans).

```java
public interface AgenticanEventListener {
    void on(AgenticanEvent event);
}
```

Quarkus auto-subscribes every CDI bean of this type — drop in `@ApplicationScoped` and
the producer wires it onto the bus for you:

```java
@ApplicationScoped
public class TurnTokenLogger implements AgenticanEventListener {

    private static final Logger log = LoggerFactory.getLogger(TurnTokenLogger.class);

    @Override
    public void on(AgenticanEvent event) {

        if (event instanceof ResponseReceived r) {
            log.info("Turn {} used {} output tokens",
                    r.turnId(), r.response().outputTokens());
        }
    }
}
```

Every event carries the full payload it describes — `ResponseReceived` includes the
whole `LlmResponse`, `ToolCallStarted` includes the `ToolCall`, `TaskStarted` includes
the `WorkflowDefinition` plus params. There is no need to read back from
`WorkflowRunStore` inside the handler (and you shouldn't — see
[observability.md](../observability.md) for the event-payload sufficiency rule).

**Used by:** `quarkus-otel` (step + run + turn + LLM-call spans), `quarkus-metrics`
(run + turn + tool counters), `quarkus-rest` (CDI event bridge → SSE timeline).

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

## HitlResponseDispatcher

Routes a human's HITL response to whichever runtime is awaiting it. The default
`InProcessHitlResponseDispatcher` (produced as a `@DefaultBean` by `AgenticanBeansProducer`)
hands responses to the in-process `HitlManager`. When `agentican-temporal` is in play and
a task's `runtime == TEMPORAL`, the `HitlManager` has no parked thread to wake — the
agent loop is awaiting a Temporal workflow signal in some worker.

```java
public interface HitlResponseDispatcher {
    void respond(String checkpointId, HitlResponse response);
    void cancel(String checkpointId);
}
```

Override with a runtime-aware composite to route correctly:

```java
@Produces @ApplicationScoped @Alternative
@Priority(Interceptor.Priority.LIBRARY_AFTER + 10)
HitlResponseDispatcher temporalAware(HitlManager hm, WorkflowRunStore store,
                                     WorkflowClient client, AgenticanEventBus bus) {
    var d = new TemporalAwareHitlResponseDispatcher(
            new InProcessHitlResponseDispatcher(hm), store, client);
    bus.subscribe(d);   // indexes TEMPORAL checkpoints as HitlNotified events arrive
    return d;
}
```

The REST controllers (`CheckpointsResource`, `AgenticanWebSocket`) inject `HitlResponseDispatcher`
and call `respond`/`cancel` — they don't know which runtime owns the checkpoint and don't need to.

**Used by:** `quarkus-rest` (HTTP + WebSocket HITL endpoints). See [HITL](../hitl.md#routing-responses-hitlresponsedispatcher).

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
LlmClientDecorator:        metrics wraps → otel wraps → raw client
WorkflowRunDecorator:      composed via snapshot() chain
AgenticanEventListener:    every listener is subscribed; bus dispatches in FIFO order
```

No configuration needed — it's automatic via `Instance<T>.stream()`.
