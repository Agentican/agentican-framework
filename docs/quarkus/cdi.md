# CDI Integration

## `@Inject Agentican`

The core module produces a singleton `Agentican` bean via `AgenticanProducer`. It's built
from your `agentican.*` config at startup and disposed on shutdown.

```java
@Inject Agentican agentican;

var run = agentican.run("Find papers on agents");
var taskId = run.id();              // 8-char id, available immediately
var output = run.await();           // blocks; returns the typed String output
var result = run.untypedResult();   // blocks; returns the full WorkflowRunResult
```

### Async access

`WorkflowRun.future()` returns a `CompletableFuture<R>` (typed); `untypedFuture()` returns the full `WorkflowRunResult`:

```java
agentican.run(description).future()
    .thenAccept(output -> log.info("Done: {}", output));

agentican.run(description).untypedFuture()
    .thenAccept(result -> log.info("Status: {}", result.status()));
```

## `@Inject AgenticanRecovery`

`AgenticanRecovery` is the server-side recovery companion to `Agentican`. The Quarkus runtime produces it as a singleton bean from the injected `Agentican` and disposes it on shutdown:

```java
@Inject AgenticanRecovery agenticanService;

agenticanService.resumeInterrupted();   // pick up tasks left in-flight after restart
agenticanService.reapOrphans();         // mark unrecoverable tasks FAILED
```

You don't usually need to call these yourself — `ResumeOnStartObserver` runs `resumeInterrupted` automatically on `StartupEvent`. Toggle that behavior with `agentican.resume-on-start=false` and tune fan-out with `agentican.resume-max-concurrent`.

## `@AgenticanWorkflow` qualifier — typed `Workflow<P, R>`

Inject a typed, reusable handle bound to a specific workflow. Two type parameters: input record `P` and output record `R` (`Void` for either if not needed).

```java
record TriageParams(String customerId, String priority) {}
record TriageOutput(String classification, String reason) {}

@Inject @AgenticanWorkflow(name = "triage")
Workflow<TriageParams, TriageOutput> triage;

TriageOutput out = triage.start(new TriageParams("cust-42", "HIGH")).await();
```

- The qualifier value is the workflow **name**. The workflow must be registered (YAML, fluent builder, JPA catalog, programmatic registration) by the time CDI resolves the bean — otherwise injection fails fast with `IllegalStateException`.
- The handle captures the resolved `WorkflowDefinition` at injection time. The output step and structured-output binding are computed once.
- `P` is the typed params record. Jackson's `SNAKE_CASE` strategy maps `customerId` → `customer_id`. Use `Void` for parameterless workflows; use `Map<String, Object>` as a dynamic-map escape hatch.
- `R` is the typed output. The framework reads the workflow's `outputStep` and Jackson-parses the text into `R`. Use `Void` to skip output parsing.

Failure modes from `await()`:
- Run didn't complete → `WorkflowOutputException` (carries the failed `WorkflowRunResult`).
- Output isn't valid JSON for `R` → `WorkflowOutputException` (carries the raw output + target class).

For programmatic lookups, build from code instead of injecting:

```java
@Inject Agentican agentican;

// Typed output
var workflow = agentican.workflow("some-workflow")
        .input(MyParams.class)
        .output(MyOutput.class)
        .build();

// Untyped output — omit .output(...) to default R = Void
var workflow = agentican.workflow("some-workflow")
        .input(MyParams.class)
        .build();
```

## `@AgenticanTask` qualifier — single-step ad-hoc workflow

For one-shot agent invocations without a pre-registered workflow definition, use `@AgenticanTask` to declaratively bind a single agent step:

```java
@Inject
@AgenticanTask(name = "Research Question",
      agent = "researcher",
      instructions = "Research {{input}} and summarize the findings.")
Workflow<String, String> researcher;

String summary = researcher.start("vector databases").await();
```

The producer constructs a single-step `WorkflowDefinition` from the annotation parameters at injection time.

## ReactiveAgentican

Mutiny-native wrapper for reactive Quarkus applications. Returns `Uni<T>` for natural
composition with Vert.x, RESTEasy Reactive, and reactive pipelines.

```java
@Inject ReactiveAgentican agentican;

// Non-blocking: returns Uni<WorkflowRun<String>> immediately
public Uni<Response> submit(String description) {
    return agentican.run(description)
        .onItem().transform(run ->
            Response.created(URI.create("/tasks/" + run.id())).build());
}

// Awaiting: Uni completes when the run finishes
public Uni<String> research(String topic) {
    return agentican.runAndAwait("Research " + topic);
}
```

All workflow execution runs on the framework's virtual thread executor, never on the Vert.x
event loop.

## Typed reactive workflow — `ReactiveWorkflow<P, R>`

The reactive counterpart to `@AgenticanWorkflow(name = "...") Workflow<P, R>`. Same qualifier, same generic params, just returns `Uni<...>` so you can compose without blocking:

```java
@Inject @AgenticanWorkflow(name = "triage")
ReactiveWorkflow<TriageParams, TriageOutput> triage;

@GET
@Path("/triage/{customer}")
public Uni<TriageOutput> triage(@PathParam("customer") String customerId) {

    return triage.runAndAwait(new TriageParams(customerId, "HIGH"));
}
```

The `Uni` is lazy — subscription is what actually triggers submission. Workflow execution stays on virtual threads; the `Uni` simply surfaces completion to reactive pipelines.

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

Events are fired by the `CdiEventBridge`, which subscribes to the framework's
`AgenticanEventBus` and translates each `AgenticanEvent` into the corresponding CDI
event exactly once.
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

### Available events

The full event surface mirrors the framework's [`AgenticanEvent`](../../core/src/main/java/ai/agentican/framework/event/AgenticanEvent.java) hierarchy, with some `CdiEventBridge`-side enrichment so observers don't have to maintain cross-event state themselves. Source of truth: [`CdiEventBridge.java`](../../quarkus-runtime/src/main/java/ai/agentican/quarkus/event/CdiEventBridge.java).

| Event | Fires when | Key fields |
|---|---|---|
| `WfRunStartedEvent` | Workflow planner began (translates from `PlanStarted`) | `taskId`, `taskDescription` |
| `WfRunCompletedEvent` | Plan resolved (`PlanCompleted`) | `taskId`, `taskName`, `planId` |
| `TaskStartedEvent` | Task entered the executor | `taskId`, `taskName`, `parentTaskId`, `log` |
| `TaskCompletedEvent` | Task hits COMPLETED / FAILED / CANCELLED | `taskId`, `taskName`, `status`, `log`, `succeeded()` |
| `TaskResumedEvent` | A recovered task is being re-dispatched | `taskId` |
| `TaskReapedEvent` | A non-resumable task was reaped on startup | `taskId`, `reason` |
| `StepStartedEvent` | Step began | `stepId`, `taskId`, `stepName` |
| `StepCompletedEvent` | Step hits terminal status | `stepId`, `taskId`, `stepName`, `status` |
| `RunStartedEvent` | An agent run started within a step | `runId`, `stepId`, `agentName`, `runIndex`, `taskId` |
| `RunCompletedEvent` | Run finished | `runId`, `stepId`, `agentName`, `runIndex`, `taskId` |
| `TurnStartedEvent` | One LLM round-trip began within a run | `turnId`, `runId`, `agentName`, `turn`, `taskId` |
| `TurnCompletedEvent` | Turn finished | `turnId`, `runId`, `agentName`, `turn`, `taskId` |
| `MessageSentEvent` | LLM request dispatched | `messageId`, `turnId`, `agentName`, `turn`, `taskId` |
| `ResponseReceivedEvent` | LLM responded | `responseId`, `turnId`, `agentName`, `turn`, `stopReason`, `inputTokens`, `outputTokens`, `toolCallCount`, `taskId` |
| `ToolCallStartedEvent` | Tool invocation began | `toolCallId`, `turnId`, `toolName`, `taskId` |
| `ToolCallCompletedEvent` | Tool invocation finished | `toolCallId`, `turnId`, `toolName`, `isError`, `taskId` |
| `HitlCheckpointEvent` | Step parked on HITL | `taskId`, `stepId`, `stepName`, `checkpoint` |
| `IterationStartedEvent` | A loop-body sub-task began | `taskId`, `parentStepId`, `parentTaskId`, `taskName`, `iterationIndex` |
| `IterationCompletedEvent` | Loop-body sub-task finished | `taskId`, `parentStepId`, `parentTaskId`, `status` |

Events fire exactly once per lifecycle callback.

#### Why some fields are populated and others are 0/null

`messageId` and `responseId` are always `null` — the framework doesn't generate
separate ids for messages and responses since `turnId` uniquely identifies the
single LLM round-trip per turn. `runIndex` is always `0` — the framework doesn't
number multiple runs of the same step (resume scenarios produce additional runs
but their ordering can be inferred from arrival order or from the persisted
`RunLog.createdAt` timestamps). The fields stay on the events so downstream
consumers that *do* want to assign their own ids can do so without an API change.

#### Payload enrichment pattern (internals)

`CdiEventBridge` keeps small `ConcurrentHashMap`s keyed by `runId`, `turnId`, and `toolCallId` so it can attach denormalized fields (agent name, step id, turn index, parent linkage) onto downstream CDI events without re-reading from `WorkflowRunStore` on every event. The maps drain as completion events arrive; `TaskCompleted` evicts any task-scoped state. The pattern lets the CDI event shape stay flat (no nested entity references) while the framework events stay normalized.

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
| `KnowledgeStore` | `KnowledgeStoreMemory` | `JpaKnowledgeStore` (store-jpa) or your own |
| `WorkflowRunStore` | `WorkflowRunStoreMemory` | `JpaWfRunStore` (store-jpa) or your own |
| `AgentRegistry` | `AgentRegistryMemory` | `JpaAgentRegistry` (store-jpa) or your own |
| `SkillRegistry` | `SkillRegistryMemory` | `JpaSkillRegistry` (store-jpa) or your own |
| `WorkflowRegistry` | `WorkflowRegistryMemory` | `JpaWorkflowRegistry` (store-jpa) or your own |

The JPA beans in `agentican-quarkus-store-jpa` are gated with
`@IfBuildProperty(name = "agentican.store.backend", stringValue = "jpa",
enableIfMissing = true)` — they activate whenever the jar is on the classpath
unless you explicitly set `agentican.store.backend=memory`.

## Health checks

Automatically registered (no opt-in needed):

- **Liveness** (`/q/health/live`, name `"agentican"`) — UP when the `Agentican` bean is initialized.
- **Readiness** (`/q/health/ready`, name `"agentican-readiness"`) — UP when:
  - `Agentican` is initialized, **and**
  - at least one LLM is configured in `EngineConfig`, **and**
  - every agent declared in `CatalogConfig` is present in the `AgentRegistry`.

  The readiness check's payload reports `llms` (count) and `agents` (registered count) on success, or `reason` + the relevant counts on failure. Example UP:
  ```json
  {"name": "agentican-readiness", "status": "UP", "data": {"llms": 2, "agents": 5}}
  ```
  Example DOWN (catalog YAML declares 5 agents, only 3 finished registering):
  ```json
  {"name": "agentican-readiness", "status": "DOWN",
   "data": {"reason": "Configured agents not all registered", "declared": 5, "registered": 3}}
  ```

  This is intentionally a *shallow* check — it doesn't verify per-agent LLM mappings, toolkit availability, or database connectivity. Apps that need deeper signals should produce their own `HealthCheck` beans.

## Bean validation

`@NotBlank` and `@Min` annotations on config properties. Invalid config fails at startup:

```
jakarta.validation.ConstraintViolationException:
  agentican.llm[0].api-key must not be blank
```
