# CDI Integration

## `@Inject Agentican`

The core module produces a singleton `Agentican` bean via `AgenticanProducer`. It's built
from your `agentican.*` config at startup and disposed on shutdown.

```java
@Inject Agentican agentican;

var handle = agentican.run("Find papers on agents");
var taskId = handle.taskId();      // available immediately
var result = handle.result();       // blocks until done
```

### Async access

`TaskHandle.resultAsync()` returns a `CompletableFuture<TaskResult>`:

```java
agentican.run(task).resultAsync()
    .thenAccept(result -> log.info("Done: {}", result.status()));
```

## `@Inject AgenticanService`

`AgenticanService` is the server-side recovery companion to `Agentican`. The Quarkus runtime produces it as a singleton bean from the injected `Agentican` and disposes it on shutdown:

```java
@Inject AgenticanService agenticanService;

agenticanService.resumeInterrupted();   // pick up tasks left in-flight after restart
agenticanService.reapOrphans();         // mark unrecoverable tasks FAILED
```

You don't usually need to call these yourself — `ResumeOnStartObserver` runs `resumeInterrupted` automatically on `StartupEvent`. Toggle that behavior with `agentican.resume-on-start=false` and tune fan-out with `agentican.resume-max-concurrent`.

## `@AgenticanAgent` qualifier

Inject pre-registered agents by name without going through the registry:

```java
@Inject
@AgenticanAgent("researcher")
Agent researcher;

// Use the agent's name, role, skills
log.info("Agent: {} — {}", researcher.name(), researcher.role());
```

The agent must be declared in `agentican.agents[*]` configuration.

## ReactiveAgentican

Mutiny-native wrapper for reactive Quarkus applications. Returns `Uni<T>` for natural
composition with Vert.x, RESTEasy Reactive, and reactive pipelines.

```java
@Inject ReactiveAgentican agentican;

// Non-blocking: returns Uni<TaskHandle> immediately
public Uni<Response> submit(String description) {
    return agentican.run(description)
        .onItem().transform(handle ->
            Response.created(URI.create("/tasks/" + handle.taskId())).build());
}

// Awaiting: Uni completes when the task finishes
public Uni<String> research(String topic) {
    return agentican.runAndAwait("Research " + topic)
        .onItem().transform(TaskResult::lastOutput);
}
```

| Method | Returns | Blocks? |
|---|---|---|
| `run(String)` | `Uni<TaskHandle>` | No — task runs on virtual thread |
| `run(Task)` | `Uni<TaskHandle>` | No |
| `run(Task, Map)` | `Uni<TaskHandle>` | No |
| `runAndAwait(String)` | `Uni<TaskResult>` | No — Uni completes when task finishes |
| `runAndAwait(Task)` | `Uni<TaskResult>` | No |
| `runAndAwait(Task, Map)` | `Uni<TaskResult>` | No |

All task execution runs on the framework's virtual thread executor, never on the Vert.x
event loop.

## CDI lifecycle events

Events are fired by the `CdiEventBridge`, which bridges framework `StepListener` callbacks
to CDI events. Each lifecycle callback fires the corresponding CDI event exactly once.
Observe them with `@Observes`:

```java
void onStarted(@Observes TaskStartedEvent event) {
    log.info("Task {} started", event.taskId());
}

void onCompleted(@Observes TaskCompletedEvent event) {
    if (event.succeeded()) {
        audit.record(event.taskId(), event.taskName());
    } else {
        alerting.notify("Task {} failed: {}", event.taskId(), event.status());
    }
}

void onStep(@Observes StepCompletedEvent event) {
    log.info("Step {} → {}", event.stepName(), event.status());
}

void onCheckpoint(@Observes HitlCheckpointEvent event) {
    pushNotification.send("Approval needed: " + event.checkpoint().description());
}
```

| Event | Fires when | Key fields |
|---|---|---|
| `TaskStartedEvent` | First save of task log | `taskId`, `taskName` |
| `TaskCompletedEvent` | Task hits COMPLETED/FAILED/CANCELLED | `taskId`, `taskName`, `status`, `succeeded()` |
| `StepCompletedEvent` | Step hits terminal status | `taskId`, `stepName`, `status`, `succeeded()` |
| `HitlCheckpointEvent` | Step parks on HITL checkpoint | `taskId`, `stepName`, `checkpoint` |

Events fire exactly once per lifecycle callback.

## Bean overrides

`AgenticanProducer` injects every framework collaborator and passes it to the
`Agentican.builder()`. `AgenticanBeansProducer` supplies in-memory `@DefaultBean`
fallbacks for all of them, so an app with only `agentican-quarkus-runtime` on
the classpath still works.

Override by producing your own bean — whichever module wins in ArC discovery
replaces the default:

```java
// Custom HITL manager with web push notifications
@Produces
@ApplicationScoped
public HitlManager myHitlManager() {
    return new HitlManager((mgr, checkpoint) -> {
        webPush.send(checkpoint);
        // Don't call respond() here — the REST endpoint will
    }, Duration.ofHours(24));
}
```

| Bean | `@DefaultBean` | Superseded by |
|---|---|---|
| `HitlManager` | Logging notifier | your `@Produces HitlManager` |
| `KnowledgeStore` | `MemKnowledgeStore` | `JpaKnowledgeStore` (store-jpa) or your own |
| `TaskStateStore` | `MemTaskStateStore` | `JpaTaskStateStore` (store-jpa) or your own |
| `AgentRegistry` | `InMemoryAgentRegistry` | `JpaAgentRegistry` (store-jpa) or your own |
| `SkillRegistry` | `InMemorySkillRegistry` | `JpaSkillRegistry` (store-jpa) or your own |
| `PlanRegistry` | `InMemoryPlanRegistry` | `JpaPlanRegistry` (store-jpa) or your own |

The JPA beans in `agentican-quarkus-store-jpa` are gated with
`@IfBuildProperty(name = "agentican.store.backend", stringValue = "jpa",
enableIfMissing = true)` — they activate whenever the jar is on the classpath
unless you explicitly set `agentican.store.backend=memory`.

## Health checks

Automatically registered:

- **Liveness** (`/q/health/live`) — UP when `Agentican` bean is initialized
- **Readiness** (`/q/health/ready`) — UP when at least one LLM is configured and all
  declared agents are registered

## Bean validation

`@NotBlank` and `@Min` annotations on config properties. Invalid config fails at startup:

```
jakarta.validation.ConstraintViolationException:
  agentican.llm[0].api-key must not be blank
```
