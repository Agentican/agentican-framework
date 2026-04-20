# CDI Integration

## `@Inject AgenticanRuntime`

The core module produces a singleton `Agentican` bean via `AgenticanProducer`. It's built
from your `agentican.*` config at startup and disposed on shutdown.

```java
@Inject AgenticanRuntime agentican;

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

## `@Inject AgenticanRecovery`

`AgenticanRecovery` is the server-side recovery companion to `Agentican`. The Quarkus runtime produces it as a singleton bean from the injected `Agentican` and disposes it on shutdown:

```java
@Inject AgenticanRecovery agenticanService;

agenticanService.resumeInterrupted();   // pick up tasks left in-flight after restart
agenticanService.reapOrphans();         // mark unrecoverable tasks FAILED
```

You don't usually need to call these yourself — `ResumeOnStartObserver` runs `resumeInterrupted` automatically on `StartupEvent`. Toggle that behavior with `agentican.resume-on-start=false` and tune fan-out with `agentican.resume-max-concurrent`.

## `@AgenticanPlan` qualifier — typed `Agentican<P, R>`

Inject a typed, reusable caller bound to a specific plan. Two type parameters: input record `P` and output record `R` (`Void` for either if not needed).

```java
record TriageParams(String customerId, String priority) {}
record TriageOutput(String classification, String reason) {}

@Inject @AgenticanPlan("triage")
Agentican<TriageParams, TriageOutput> triage;

TriageOutput out = triage.runAndAwait(new TriageParams("cust-42", "HIGH"));
```

- The qualifier value is the plan **name** (not `externalId`). Any plan in `AgenticanRuntime.registry().plans()` qualifies — YAML, fluent builder, JPA catalog, programmatic registration, or planner output (if it has a known name).
- The plan is resolved from the registry on each invocation, so plans added or updated at runtime are honored.
- `P` is the typed params record. Jackson's `SNAKE_CASE` strategy maps `customerId` → `customer_id`. Use `Void` for parameterless plans; use `Map<String, Object>` as a dynamic-map escape hatch.
- `R` is the typed output. The framework reads the plan's output step (declared via `Plan.builder(...).outputStep(name)` or implicit when the plan has one step) and Jackson-parses the text into `R`. Use `Void` to skip output parsing.
- If the plan isn't in the registry at boot, the producer logs a WARN and defers resolution to the first `run()` — at which point a missing plan throws `IllegalStateException`.

Failure modes from `runAndAwait`:
- Task didn't complete → `TaskFailedException` (carries the failed `TaskResult`).
- Output isn't valid JSON for `R` → `OutputParseException` (carries the raw output + target class).

For dynamic plan lookups, construct in code instead of injecting:

```java
@Inject AgenticanRuntime runtime;

// Typed output
var invoker = runtime.agentican("some-runtime-plan", MyParams.class, MyOutput.class);

// Untyped output (current behavior)
var invoker = runtime.agentican("some-runtime-plan", MyParams.class);
```

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

## ReactiveAgenticanRuntime

Mutiny-native wrapper for reactive Quarkus applications. Returns `Uni<T>` for natural
composition with Vert.x, RESTEasy Reactive, and reactive pipelines.

```java
@Inject ReactiveAgenticanRuntime agentican;

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

## Typed reactive invoker — `ReactiveAgentican<P, R>`

The reactive counterpart to `@AgenticanPlan("name") Agentican<P, R>`. Same qualifier, same generic params, just returns `Uni<...>` so you can compose without blocking:

```java
@Inject @AgenticanPlan("triage")
ReactiveAgentican<TriageParams, TriageOutput> triage;

@GET
@Path("/triage/{customer}")
public Uni<TriageOutput> triage(@PathParam("customer") String customerId) {

    return triage.runAndAwait(new TriageParams(customerId, "HIGH"));
}
```

Same three method shapes as the synchronous variant:

| Method | Returns |
|---|---|
| `run(P)` | `Uni<TaskHandle>` — resolves once submission lands |
| `awaitTaskResult(P)` | `Uni<TaskResult>` — resolves when the task completes |
| `runAndAwait(P)` | `Uni<R>` — resolves with the typed, schema-validated result |

The `Uni` is lazy — subscription is what actually triggers submission. Task execution stays on virtual threads; the `Uni` simply surfaces completion to reactive pipelines.

## Reactive HITL notifier

If your HITL notifier posts to a reactive backend (Vert.x event bus, reactive Redis, a Mutiny-returning messaging client), declare a `ReactiveHitlNotifier` bean instead of a sync `HitlNotifier`:

```java
@ApplicationScoped
public class MyHitlNotifier implements ReactiveHitlNotifier {

    @Inject Mailer mailer;

    @Override
    public Uni<Void> onCheckpoint(HitlManager manager, HitlCheckpoint checkpoint) {

        return mailer.send(Mail.withText("ops@company.com",
                "Approval needed: " + checkpoint.description(),
                checkpoint.content()));
    }
}
```

The default `HitlManager` producer auto-detects a CDI bean of either type (prefers sync if both are declared) and wires it. No custom `HitlManager` producer is required. When the framework fires a checkpoint it subscribes to the returned `Uni` and waits for completion — which is fine on a virtual thread, since the task is about to park anyway waiting for the human response.

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
`AgenticanRuntime.builder()`. `AgenticanBeansProducer` supplies in-memory `@DefaultBean`
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
| `KnowledgeStore` | `KnowledgeStoreMemory` | `JpaKnowledgeStore` (store-jpa) or your own |
| `TaskStateStore` | `TaskStateStoreMemory` | `JpaTaskStateStore` (store-jpa) or your own |
| `AgentRegistry` | `AgentRegistryMemory` | `JpaAgentRegistry` (store-jpa) or your own |
| `SkillRegistry` | `SkillRegistryMemory` | `JpaSkillRegistry` (store-jpa) or your own |
| `PlanRegistry` | `PlanRegistryMemory` | `JpaPlanRegistry` (store-jpa) or your own |

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
