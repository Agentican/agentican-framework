# Temporal Integration

The `agentican-temporal` module lets you run Agentican agents and plans on a [Temporal](https://temporal.io) cluster instead of (or alongside) the in-process executor. You get Temporal's durability, retries, visibility, and signal/query primitives without giving up the Agentican catalog model (agents, skills, plans, knowledge, tools).

## When to use Temporal

Pick the in-process executor (`agentican-framework-core` alone) when:

- A single JVM owns the task lifecycle from start to finish.
- You can rely on `WorkflowRunStore` (in-memory or JPA) for crash recovery.
- Workflow visibility means reading your own DB or following the SSE event stream.

Pick the Temporal integration when:

- You need workflows that survive worker restarts, span weeks, or wait on signals for long stretches.
- You want per-activity retry policies, timeouts and history replay for free.
- You already operate Temporal and want agent workloads to land in the same control plane.
- You need to split work across hosts: an LLM-heavy worker, a tool-heavy worker, a CPU-heavy code-step worker.

You can also use both: in-process for fast inline tasks, Temporal for the long-running ones — they share the same `Agentican` catalog.

## Installation

```xml
<dependency>
    <groupId>ai.agentican</groupId>
    <artifactId>agentican-temporal</artifactId>
    <version>0.1.0-alpha.1</version>
</dependency>
```

Requires Java 21+. Brings `io.temporal:temporal-sdk` transitively.

## Two axes of choice

`agentican-temporal` ships four combinations along two independent axes:

| | **Coarse** (one activity per agent step) | **Fine-grained** (one activity per LLM call, per tool call) |
|---|---|---|
| **Generic** (framework-provided workflow class, plan loaded from YAML) | `AgenticanWorkflow` + `AgenticanWorkflowImpl` | `AgenticanWorkflow` + `FineGrainedAgenticanWorkflowImpl` (dispatches a child `RunnerBasedAgentWorkflow` per agent step) |
| **Custom** (your own workflow class per plan) | hand-coded workflow calling `AgentStepActivity` directly | `RunnerBasedAgentWorkflow` for one agent, or hand-rolled LLM/tool loop in the workflow body |

The axes:

- **Coarse vs fine** — does a Temporal activity wrap the *entire* agent step (system prompt assembly, LLM loop, tool calls, scratchpad, HITL — all opaque), or is each LLM round-trip and each tool call its own activity?
- **Generic vs custom** — do you reuse the framework's plan interpreter (`AgenticanWorkflowImpl`) that runs *any* `WorkflowDefinition`, or do you author a workflow class that knows about your specific plan?

You can pick differently per workflow. The same worker can register all four.

## `TemporalAgentican` — the worker-side adapter

The whole point of `TemporalAgentican` is to remove resolver/store/client boilerplate when wiring a worker. One factory call replaces the per-activity setup:

```java
import ai.agentican.temporal.TemporalAgentican;

try (var agentican = Agentican.builder()
        .configuration().yaml().path(enginePath).end()
        .registry().yaml().path(catalogPath).end()
        .toolkit("web-search", new YourWebSearchToolkit())   // optional
        .build()) {

    var temporal = TemporalAgentican.of(agentican);

    var worker = factory.newWorker("my-task-queue");

    worker.registerWorkflowImplementationTypes(AgenticanWorkflowImpl.class);

    worker.registerActivitiesImplementations(
            temporal.agentStepActivity());

    factory.start();
}
```

Agents and toolkits resolve via `agentican.registry()`. The LLM client comes from `agentican.llm(name)`. Stores (workflow-run, knowledge) default to in-memory; pass JPA-backed instances via the explicit constructor:

```java
new TemporalAgentican(agentican, myJpaWorkflowRunStore, myJpaKnowledgeStore);
```

### Factory methods

| Method | Returns | For |
|---|---|---|
| `agentStepActivity()` | `AgentStepActivityImpl` | coarse mode — wraps an entire agent step |
| `agentConfigActivity()` | `AgentConfigActivityImpl` | fine-grained generic — parent looks up agent configs for child workflows |
| `llmCallActivity()` | `LlmCallActivityImpl` | fine-grained — one Temporal activity per LLM round-trip |
| `toolCallActivity()` | `ToolCallActivityImpl` | fine-grained — one Temporal activity per tool call |
| `agenticanActivity()` | `AgenticanActivityImpl` | fine-grained — the bridge that funnels workflow-side `AgenticanEvent`s onto Agentican's main bus so the in-process listener set (persister, knowledge ingestor, metrics, OTel, custom hooks) handles them identically to in-process events. Also exposes `loadRunLog` for resume reads. |
| `knowledgeStoreActivity()` | `KnowledgeStoreActivityImpl` | fine-grained — runner reads knowledge entries via this |
| `agenticanWorkflowInput(planName, params)` | `AgenticanWorkflowInput` | look up a plan by name and wrap with params, ready to hand to `AgenticanWorkflow.run()` |

## Activities reference

Each activity is a thin Temporal wrapper around an Agentican primitive. The interfaces live in `ai.agentican.temporal.activity`.

### `AgentStepActivity` (coarse)

`invokeAgent(AgentInvocationRequest)` runs an entire agent step end-to-end inside one activity. The activity worker resolves the agent + toolkits from its registry and calls `agent.run(...)`. Use this when you want each plan step to be one Temporal history event.

### `CodeStepActivity` (coarse)

`invokeCode(CodeInvocationRequest)` runs a registered `CodeStep<I, O>` inside one activity. The activity worker holds the `CodeStepRegistry` and a `CodeStepContextProvider`.

### `LlmCallActivity` (fine-grained)

`send(LlmRequest)` is one LLM round-trip. The runner inside `RunnerBasedAgentWorkflowImpl` (or your custom fine-grained workflow) calls this once per turn. Per-LLM-call retry policies are configured via Temporal activity options.

### `ToolCallActivity` (fine-grained)

`execute(ToolCallRequest)` is one tool invocation. Useful both as a building block for fine-grained agent loops and for explicit per-tool retry policies / heartbeat timeouts.

### `AgenticanActivity` (fine-grained)

The bridge that funnels workflow-side `AgenticanEvent`s onto the application's main `AgenticanEventBus`. The workflow publishes events on a small per-execution bus whose only subscriber is a forwarder that calls `AgenticanActivity.publish(event)`; on the worker side, the activity implementation publishes the same event on the main bus, where the in-process listener set (`WorkflowRunStorePersister`, `KnowledgeIngestor`, metrics, OTel, and any custom listeners) processes it identically to in-process events. The activity also exposes `loadRunLog(taskId)` for the runner's resume reads. Required by `RunnerBasedAgentWorkflowImpl`.

### `KnowledgeStoreActivity` (fine-grained)

Wraps `KnowledgeStore.get(entryId)` so the runner can resolve `RECALL_KNOWLEDGE` tool calls. Required by `RunnerBasedAgentWorkflowImpl`.

### `AgentConfigActivity` (used by the fine-grained interpreter)

Returns an `AgentConfig` for a given agent ref. Used by `FineGrainedAgenticanWorkflowImpl` to materialize child-workflow inputs without holding live `Agent` references in the workflow body (which would break determinism).

## Workflow classes

### `AgenticanWorkflow` (interface)

Generic interface that executes a `WorkflowDefinition`. Two implementations ship:

- `AgenticanWorkflowImpl` — **coarse**. Each `WorkflowStepAgent` becomes one `AgentStepActivity.invokeAgent` call. Each `WorkflowStepCode` becomes one `CodeStepActivity.invokeCode`. Loop/branch interpretation happens in the workflow body. **Best for**: most production cases — clean Temporal history with one event per plan step, no extra child-workflow tax.

- `FineGrainedAgenticanWorkflowImpl` — **fine-grained**. Same interface and inputs, but each agent step dispatches a child `RunnerBasedAgentWorkflow` so every LLM round-trip and tool call shows up in that child's history. **Best for**: debugging, audit trails, per-call retry policies — at the cost of one child-workflow execution per plan step.

Register whichever variant matches the visibility you want. The YAML plan and the input shape are identical.

### `RunnerBasedAgentWorkflow`

A standalone workflow that drives one `SmacAgentRunner` via `TemporalAgentLoopHost`. Use it directly when you want a single-agent fine-grained loop without a plan around it, or wire it as a child workflow under `FineGrainedAgenticanWorkflowImpl`.

Input: `RunnerBasedAgentInput(AgentConfig, task, taskId, stepId, stepName, skills, toolDefinitions, toolHitlTypes, timeout, maxTurns)`.

## Replay safety and persistence granularity

The two workflow classes have meaningfully different durability characteristics — worth knowing which fits your use case.

| | **`AgenticanWorkflowImpl`** (coarse) | **`RunnerBasedAgentWorkflowImpl`** (fine-grained) |
|---|---|---|
| Who orchestrates the plan | Temporal workflow | The framework's in-process orchestrator |
| Who runs the agent loop | `AgentStepActivityImpl` (in-process inside an activity) | `SmacAgentRunner` directly inside the workflow body |
| Each agent step | One activity call to `invokeAgent` | Many activity calls (one per LLM call, tool call, lifecycle event) |
| Workflow history size | Compact — one entry per plan step | Detailed — every event becomes an activity invocation |
| **Replay safety if an activity crashes mid-step** | **Not preserved** — the in-process agent loop state inside the crashed activity is lost; Temporal re-invokes `invokeAgent` from scratch and the agent loop replays from the beginning of that step (unless your `WorkflowRunStore` is durable and the runner's own resume path picks up partial state) | **Preserved** — every LLM-call result, tool result, and turn boundary is recorded in workflow history; on worker restart Temporal replays from exact mid-loop state |
| `WorkflowRunLog.runtime` value | `IN_PROCESS` — the agent itself ran in-process inside an activity. `AgenticanRecovery` ignores it because the parent workflow is Temporal-managed; you should not rely on framework-level resume for these | `TEMPORAL` — Temporal owns the lifecycle end-to-end; `AgenticanRecovery` will skip these on startup |
| Best when | You want plan-orchestration durability with cheap per-step retries; multi-hour agent runs aren't your bottleneck | You can't afford to re-run an expensive multi-hour step from scratch on activity crash; you want per-LLM-call retry policies; you want detailed audit history of every turn |

Both models share the same `Agentican.eventBus()` on the worker side, so listeners — persister, knowledge ingestor, metrics, OTel — fire identically regardless of which model produced the events. The choice is about *who emits the events*, not *who handles them*. See [Event flow under Temporal](#event-flow-under-temporal) below for the mechanism.

## Event flow under Temporal

Agentican is event-driven: every lifecycle moment (task started, step started, run started, turn started, message sent, response received, tool called, HITL notified, step/run/task completed) is published as an `AgenticanEvent` on an `AgenticanEventBus`. Listeners subscribed to that bus do the actual work: `WorkflowRunStorePersister` writes state, `KnowledgeIngestor` extracts facts, `quarkus-metrics` increments counters, `quarkus-otel` opens spans, your custom listeners do whatever you want.

In Temporal mode, there are **two buses per running workflow** — and one of them is the same bus your in-process code uses:

```
Workflow thread (deterministic):
  TemporalAgentLoopHost.publish(event)
    → workflow-side bus
       → ForwarderListener.on(event)
         → AgenticanActivity.publish(event)        ← activity boundary
                                                     [recorded in workflow history]
                                                          ↓
Activity worker thread (normal Java):
  AgenticanActivityImpl.publish(event)
    → Agentican.eventBus()                         ← same singleton bus used by
       → WorkflowRunStorePersister                   in-process work in this JVM
       → KnowledgeIngestor
       → metrics listener, OTel listener,
         CdiEventBridge, ...
```

The workflow-side bus is sparsely subscribed — exactly one listener (the forwarder) — because Temporal workflow code has to be deterministic and replay-safe, and arbitrary listener side effects break that. The activity boundary is where determinism ends; everything past it is normal Java, so the same listener set that handles in-process events handles Temporal-borne events.

Practical consequences:

- **One listener wires both runtimes.** `bus.subscribe(myListener)` on `Agentican.Builder` (or `@ApplicationScoped` under Quarkus) covers both paths automatically.
- **No special Temporal hooks for observability.** Wire OTel / metrics / your custom logger once; events from Temporal workflows flow through the same path.
- **Knowledge ingestion just works.** `KnowledgeIngestor` is subscribed on the main bus, so step outputs from Temporal-driven steps trigger extraction the same way in-process steps do.
- **Events serialize across the activity boundary.** `AgenticanEvent` is a sealed type with Jackson polymorphic typing; the activity argument is the event record itself. Worth checking serialization round-trips for unusually-large event payloads (very long conversation histories in `MessageSent`, deeply-nested `WorkflowDefinition` in `TaskStarted`) against Temporal's per-message payload limit (~2 MB by default).

Multiple worker JVMs each have their own `Agentican.eventBus()` instance with the same listener set — events stay local to whichever worker processed the activity, persistence to the shared store is the durability boundary. If you need one logical bus across processes (rather than each worker handling locally then persisting to a shared store), that's a broker — out of scope for the framework today.

## Runtime owner: how the framework defers to Temporal

`WorkflowRunLog` carries a `runtime` field (`IN_PROCESS` | `TEMPORAL`) populated when the task starts. The marker is set by whichever `AgentLoopHost` publishes the initial `TaskStarted` event — `InProcessAgentLoopHost` always publishes `IN_PROCESS`; `TemporalAgentLoopHost` publishes `TEMPORAL` and also fills in `temporalWorkflowId` so external code can correlate a framework task to its Temporal execution.

Three pieces of framework machinery key off this marker so they don't fight Temporal for control:

| Concern | In-process behavior | Temporal behavior |
|---|---|---|
| **Recovery** (`AgenticanRecovery.reapOrphans` / `.resumeInterrupted`) | Scans the store for tasks with `status == null` and either reaps or resumes them | **Skips** rows where `runtime == TEMPORAL` with a debug log — Temporal handles workflow recovery via history replay; touching those rows would race against Temporal |
| **Cancellation** (`AgentLoopHost.isCancelled()`) | `cancelled.get()` on the runner-supplied `AtomicBoolean` | Delegates to `CancellationScope.current().isCancelRequested()` so the agent loop short-circuits when the workflow is cancelled |
| **Per-step timeouts** (wall-clock watchdog inside `SmacAgentRunner` / `ReActAgentRunner`) | Enforced via `Instant.now().isAfter(deadline)` | Suppressed (`AgentLoopHost.isManaged() = true`). Temporal owns the deadline through activity / workflow execution timeouts; running the framework's watchdog in parallel produces conflicting termination |

The net effect: when Temporal is in charge of a task, the framework's redundant machinery defers. There's exactly one recovery mechanism, one cancellation source, one deadline enforcer per task.

## Cross-runtime HITL: `HitlResponseDispatcher`

The framework's HITL pattern is: an agent emits a checkpoint, the orchestrator parks until a human responds via REST/WebSocket/etc., the response wakes the orchestrator. In-process, that "wake" is a `CompletableFuture.complete(...)` on the parked virtual thread — but for a Temporal-owned task, the orchestrator is waiting on a Temporal workflow signal, not on an in-memory future. Calling `HitlManager.respond(...)` for a `TEMPORAL`-runtime checkpoint would silently do nothing.

`HitlResponseDispatcher` is the SPI that routes responses correctly:

```java
public interface HitlResponseDispatcher {
    void respond(String checkpointId, HitlResponse response);
    void cancel(String checkpointId);
}
```

`InProcessHitlResponseDispatcher` (the default, wired automatically under Quarkus) delegates to `HitlManager`. The temporal module ships `TemporalAwareHitlResponseDispatcher` — subscribes to `HitlNotified` on the bus to index TEMPORAL-owned checkpoints by their workflow id, then routes `respond` / `cancel` either to a Temporal signal (for TEMPORAL-tracked checkpoints) or to the in-process fallback (for everything else).

Wire it in your Quarkus app where you have access to `WorkflowClient`:

```java
@Produces @ApplicationScoped @Alternative
@Priority(Interceptor.Priority.LIBRARY_AFTER + 10)
HitlResponseDispatcher temporalAwareDispatcher(HitlManager hm, WorkflowRunStore store,
                                               WorkflowClient client, AgenticanEventBus bus) {
    var d = new TemporalAwareHitlResponseDispatcher(
            new InProcessHitlResponseDispatcher(hm), store, client);
    bus.subscribe(d);   // index TEMPORAL checkpoints as HitlNotified events arrive
    return d;
}
```

The REST controllers (`CheckpointsResource`, `AgenticanWebSocket`) call `dispatcher.respond(...)` — they don't know which runtime owns the checkpoint and don't need to. See [docs/hitl.md](hitl.md) for the broader HITL flow.

**Known limitation**: `TemporalAwareHitlResponseDispatcher` is hardcoded to signal `RunnerBasedAgentWorkflow.provideHitlResponse`. Custom Temporal workflow types that produce their own HITL checkpoints need their own dispatcher implementation (or a small registry keyed by workflow type) — the field/Javadoc trail is in place when someone needs it.

## Quick start: market-brief from the blog post

The [examples module](../examples/) contains two runners for the canonical `market-brief` workflow — both load the same YAML, both produce the same brief, they only differ in activity grain.

```java
public final class MarketBriefExample {

    public static final String TASK_QUEUE = "agentican-market-brief";

    static void main(String[] args) throws Exception {

        try (var agentican = Agentican.builder()
                .configuration().yaml().path(enginePath()).end()
                .registry().yaml().path(catalogPath()).end()
                .build()) {

            var temporal = TemporalAgentican.of(agentican);
            var client   = startWorker(temporal);

            var workflow = client.newWorkflowStub(
                    AgenticanWorkflow.class,
                    WorkflowOptions.newBuilder()
                            .setTaskQueue(TASK_QUEUE)
                            .setWorkflowId("agentican-market-brief-" + System.nanoTime())
                            .build());

            var params = new MarketBriefParams("data observability platforms", 5);

            var brief = workflow.run(
                    temporal.agenticanWorkflowInput("market-brief", params.asMap()));

            System.out.println(brief);
        }
    }

    static WorkflowClient startWorker(TemporalAgentican temporal) {

        var service = WorkflowServiceStubs.newLocalServiceStubs();
        var client  = WorkflowClient.newInstance(service);
        var factory = WorkerFactory.newInstance(client);
        var worker  = factory.newWorker(TASK_QUEUE);

        worker.registerWorkflowImplementationTypes(AgenticanWorkflowImpl.class);
        worker.registerActivitiesImplementations(temporal.agentStepActivity());

        factory.start();

        return client;
    }
}
```

For fine-grained: register `FineGrainedAgenticanWorkflowImpl` + `RunnerBasedAgentWorkflowImpl` plus the five activities the runner needs:

```java
worker.registerWorkflowImplementationTypes(
        FineGrainedAgenticanWorkflowImpl.class,
        RunnerBasedAgentWorkflowImpl.class);

worker.registerActivitiesImplementations(
        temporal.agentConfigActivity(),
        temporal.llmCallActivity(),
        temporal.toolCallActivity(),
        temporal.agenticanActivity(),
        temporal.knowledgeStoreActivity());
```

## Testing without a Temporal cluster

`io.temporal:temporal-testing` ships an in-JVM `TestWorkflowEnvironment` that replays your workflows and activities against a local clock. The framework's E2E test of `RunnerBasedAgentWorkflow` uses it; see `temporal/src/test/java/ai/agentican/temporal/workflow/RunnerBasedAgentWorkflowE2ETest.java` for a worked example.

```xml
<dependency>
    <groupId>io.temporal</groupId>
    <artifactId>temporal-testing</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <!-- Needed transitively; not pulled by temporal-testing on every classpath. -->
    <groupId>io.grpc</groupId>
    <artifactId>grpc-inprocess</artifactId>
    <scope>test</scope>
</dependency>
```

## Limitations and known gaps

- **No token streaming.** `LlmCallActivity.send(...)` returns the full response. The Temporal model doesn't naturally accommodate per-token streaming across an activity boundary.
- **HITL inside the fine-grained generic interpreter.** When `FineGrainedAgenticanWorkflowImpl` is in play, HITL responses route to the *child* `RunnerBasedAgentWorkflow`'s `provideHitlResponse` signal, not the parent. The parent's `provideHitlReply` is a no-op for this variant.
- **`TemporalAwareHitlResponseDispatcher` is single-workflow-type.** Signals `RunnerBasedAgentWorkflow.provideHitlResponse` only. Custom Temporal workflow types that also produce HITL checkpoints need their own dispatcher (or a small registry).
- **No retry/heartbeat policy presets.** Activities use `setStartToCloseTimeout` defaults from `TemporalAgentLoopHost`, with the agent step's activity timeout derived from `step.timeout()` in `AgenticanWorkflowImpl`. Wire your own `ActivityOptions` for richer policies — there's a constructor on the host that accepts pre-built stubs.
- **No Quarkus extension yet.** `agentican-temporal` is plain Java. To run workers from a Quarkus app, you wire `Agentican`, `TemporalAgentican`, and the worker manually in a `@Startup`/`@ApplicationScoped` bean.

## Where to go next

- [Examples](../examples/src/main/java/ai/agentican/framework/examples/temporal/) — runnable mains for both grains.
- [`TemporalAgentican` source](../temporal/src/main/java/ai/agentican/temporal/TemporalAgentican.java) — the adapter itself; short enough to read top-to-bottom.
- [Core Concepts](concepts.md) — how the plan/agent/runner abstractions used by the activities map back to the framework primitives.
