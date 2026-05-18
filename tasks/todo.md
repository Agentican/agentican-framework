# Event Bus Refactor — Plan

## Goal

Invert the framework's lifecycle plumbing so events are the primary integration point and the state store is "just another listener" that projects events into its persistent representation. Observability (OTel, metrics, knowledge ingestion, REST CDI bridge) and persistence become peers — all subscribers to the same event bus.

## Why now

The current `WorkflowRunStore` interface mixes two concerns: (a) authoritative state mutation and (b) lifecycle event broadcasting (via the `WorkflowRunStoreNotifying` decorator). This means:

- Adding a new lifecycle event requires updating four places: store interface, every store impl, notifying decorator, listener interface.
- A store-write failure swallows the corresponding observability event (listener fires *after* delegate write).
- The store interface has **28 mutating methods** that mirror lifecycle events 1:1 — pure coincidence enforced by hand.
- Knowledge ingestion is bolted on as a *nested* `WorkflowRunStoreNotifying` wrapping the outer one (Agentican.Builder.build() lines 259→270). Hard to extend.

## What the analysis shows

### Pluggability is partially solved in Quarkus, not in core

- `AgenticanProducer.agentican()` (quarkus-runtime, lines 178-236) injects `Instance<WorkflowRunListener>`, collects all CDI-registered listeners, and wraps them in an anonymous composite passed to `Builder.workflowRunListener(...)`. So in Quarkus, OTel + Metrics + CdiEventBridge already coexist.
- Plain-Java `Agentican.Builder` exposes only `.workflowRunListener(single)` — no `.addListener(...)`. KnowledgeIngestor is wired by a hardcoded second `WorkflowRunStoreNotifying` layer rather than by being a listener.
- The Quarkus composite is hidden plumbing — not exposed to users or available outside Quarkus. The event-bus refactor makes that plumbing first-class and uniform across plain Java and Quarkus.

### Current listener consumers

| Implementation | Module | Methods overridden | What it does | Wiring today |
|---|---|---|---|---|
| `KnowledgeIngestor` | core | `onStepCompleted` | Reads step output via `store.load()`, queues async knowledge extraction | Inner `WorkflowRunStoreNotifying` in Agentican.Builder.build() (line 270) |
| `CdiEventBridge` | quarkus-runtime | 17 of 22 | Fires CDI `Event<T>` for each lifecycle moment so other beans can `@Observes` | Auto-discovered `@ApplicationScoped` bean |
| `MeteredTurnListener` | quarkus-metrics | `onRunCompleted`, `onResponseReceived`, `onToolCallStarted`, `onToolCallCompleted` | Micrometer counters/timers for runs, turns, tokens, tool calls | `@Produces` in MetricsAutoConfiguration |
| `TracedLifecycleListener` | quarkus-otel | 15 of 22 | Opens/closes OTel spans, attaches token usage and error attrs | `@Produces` in TracingAutoConfiguration |

### Current publishers (places that fire what will become events)

| Class | Module | Write call sites | Notes |
|---|---|---|---|
| `WorkflowRunner` | core | 16 | Plan-level — taskStarted, stepStarted/Completed, taskCompleted, hitlNotified/Responded, reaping |
| `SmacAgentRunner` | core | 26 | Via `AgentLoopHost.host.*` — turn-level (turnStarted/Completed/Abandoned, messageSent, responseReceived, toolCallStarted/Completed) |
| `ReActAgentRunner` | core | 14 | Same surface as SmacAgentRunner |
| `InProcessAgentLoopHost` | core | 13 | The host implementation that today translates host calls to store calls |
| `TemporalAgentLoopHost` | temporal | 13 | Translates host calls to Temporal activity calls (which then call the store on the activity worker) |
| `WorkflowRunStoreNotifying` | core | 17 | The decorator — disappears in the new model |
| `StepBranchRunner` | core | 1 | `branchPathChosen` |
| `AgenticanRecovery` | core | 4 | Reap-on-startup writes (FAILED step + task completions) |

### Read consumers (these keep using the store directly)

`AgenticanRecovery` (resume), `WorkflowRunner` (sub-task dedup), `KnowledgeIngestor` (post-step output read), `InProcessAgentLoopHost` (agent introspection), `TasksResource` (REST), `TaskEventBus` (Quarkus), `MeteredTurnListener`/`TracedLifecycleListener` (side-channel reads), `AgenticanDevUIService` (dev UI).

→ The READ side of `WorkflowRunStore` (`load`, `list`, `listInProgress`) is unchanged. The refactor only inverts the WRITE side.

### Dead events (currently no consumers — keep for now, revisit later)

- `onToken(taskId, turnId, token)` — token streaming, no consumer.
- `onStepResumed`, `onRunResumed`, `onTurnResumed` — resume events, no consumer.
- `onHitlResponded` — only `TracedLifecycleListener` handles it; CdiEventBridge ignores.

These are carried over as event types in the new bus. Decision deferred — revisit once the refactor is in place.

### Design principle: event payload sufficiency

Every event carries everything a listener could reasonably need to do its job. **Listeners must not read from the store inside event handlers** to compensate for thin payloads — if a listener needs more data, enrich the event at the publish site.

This principle eliminates today's `store.load()` calls inside four listeners:

| Listener | Today's load() reason | Fix in new model |
|---|---|---|
| `KnowledgeIngestor` | Reads step output text after `onStepCompleted` | `StepCompleted` carries `output` → no load |
| `MeteredTurnListener` | Reads task for token totals on `onRunCompleted` | `RunCompleted` carries `tokenUsage` |
| `TracedLifecycleListener` | Reads task to enrich span attrs | Enrich event payloads with attrs the runner already knows |
| `TaskEventBus` (Quarkus) | Loads task detail per broadcast | Carry full data in event; new SSE connections still snapshot from store |

**What stays as a store read** (correct pattern, do NOT convert to listener-projection):

- `AgenticanRecovery.listInProgress()` — boot-time snapshot, no events to subscribe to before persistence exists
- `WorkflowRunner.load()` — cross-task coordination (sub-task dependency check)
- `TasksResource` / `AgenticanDevUIService` — HTTP query APIs; store IS the queryable projection
- `InProcessAgentLoopHost.load()` — agent's "read your own writes" pattern for `buildProgress`

### Risk areas worth flagging up front

1. **JPA transactional semantics**: today each store-method call is `@Transactional`. In the new model, the persister-listener will be in a transaction. If a separate listener reads the store mid-event, it might see pre-write state. **Mitigation**: define listener ordering (persister first), and document that cross-listener reads must happen *after* the persister has committed.
2. **Recovery snapshot consistency**: `AgenticanRecovery.listInProgress()` expects a coherent point-in-time view. Synchronous persister-first ordering preserves this.
3. **`runStarted` / `turnStarted` use a count-query for the index** in JPA. If a downstream listener (logger, OTel) needs the resolved index, it has to read it from the event payload (which the runner already knows when it publishes) — *not* from the store. **Decision**: include the integer index in the event payload, sourced from the runner, not the DB.
4. **Temporal**: `TemporalAgentLoopHost` publishes to a per-workflow bus that has a single subscriber forwarding to the existing `WorkflowRunStoreActivity`. The activity boundary stays where it is; we just rename the calling pattern.

---

## Target architecture

### New types in core

```
ai.agentican.framework.event/
  AgenticanEvent.java            sealed interface — base for all events
  events/
    TaskStarted.java             record (taskId, taskName, plan?, params, parentTaskId?, parentStepId?, iterationIndex)
    TaskCompleted.java           record (taskId, status)
    StepStarted.java             record (taskId, stepId, stepName)
    StepCompleted.java           record (taskId, stepId, status, output)
    StepTokenUsageAggregated.java
    RunStarted.java              record (taskId, stepId, runId, agentName)
    RunCompleted.java            record (taskId, runId, status)
    TurnStarted.java             record (taskId, runId, turnId, index)
    TurnCompleted.java           record (taskId, turnId)
    TurnAbandoned.java           record (taskId, turnId)
    MessageSent.java             record (taskId, turnId, request)
    ResponseReceived.java        record (taskId, turnId, response)
    ToolCallStarted.java         record (taskId, turnId, toolCall)
    ToolCallCompleted.java       record (taskId, turnId, toolResult)
    HitlNotified.java            record (taskId, stepId, checkpoint)
    HitlResponded.java           record (taskId, stepId, hitlId, response)
    BranchPathChosen.java        record (taskId, stepId, pathName)
    PlanStarted.java / PlanCompleted.java
    TaskReaped.java              record (taskId, reason)
    TaskResumed.java             record (taskId)

  AgenticanEventBus.java         synchronous publish/subscribe
  AgenticanEventListener.java    SAM (`void on(AgenticanEvent)`) — listeners pattern-match
```

**`AgenticanEventBus` shape:**

```java
public final class AgenticanEventBus {
    void publish(AgenticanEvent event);                       // sync, all subscribers in registration order
    void subscribe(AgenticanEventListener listener);          // ordered append
    void subscribeFirst(AgenticanEventListener listener);     // for persister — must run before observers
    int subscriberCount();
}
```

Why sealed + records: pattern matching in listener bodies gives compile-time exhaustiveness checks; records are auto-Jackson-friendly (matches Temporal pattern we already have).

Why sync: matches today's semantics. Async dispatch is a future opt-in (subscribe a queueing adapter listener).

### Listeners in the new model

| Listener | Role | Subscribes via |
|---|---|---|
| `WorkflowRunStorePersister` (new) | Translates events into `WorkflowRunStore` writes — *replaces* `WorkflowRunStoreNotifying` | `bus.subscribeFirst(...)` — must run before observers |
| `KnowledgeIngestor` | Same as today, but subscribes to `StepCompleted` instead of being a nested notifying-store layer | `bus.subscribe(...)` |
| `TracedLifecycleListener` | Same as today, just gets events from the bus | `bus.subscribe(...)` |
| `MeteredTurnListener` | Same as today | `bus.subscribe(...)` |
| `CdiEventBridge` | Translates `AgenticanEvent` to CDI `Event<T>` per type | `bus.subscribe(...)` |
| User-supplied listeners | New `.addListener(...)` builder method | `bus.subscribe(...)` |

### Publisher changes

| Class | Before | After |
|---|---|---|
| `SmacAgentRunner` / `ReActAgentRunner` | `host.turnStarted(taskId, runId, turnId)` | unchanged — they still call `host.*`. `AgentLoopHost` interface contract stays the same so runner code is untouched. |
| `InProcessAgentLoopHost` | calls `store.turnStarted(...)` | calls `bus.publish(new TurnStarted(...))` |
| `WorkflowRunner` | calls `store.stepStarted(...)` directly | calls `bus.publish(...)` |
| `StepBranchRunner` | calls `store.branchPathChosen(...)` | calls `bus.publish(...)` |
| `AgenticanRecovery` | calls `store.stepCompleted/taskCompleted` for reap | calls `bus.publish(...)` |
| `TemporalAgentLoopHost` | calls `storeActivity.*` | calls bus.publish; bus has a Temporal-routing listener that calls `storeActivity.*` |

### `WorkflowRunStore` interface

**Unchanged.** Concrete impls (`WorkflowRunStoreMemory`, `JpaWfRunStore`, `WorkflowRunStoreActivityImpl`) are unchanged. Only the *call pattern* changes — `WorkflowRunStorePersister` calls these methods on behalf of the bus.

**Deleted: `WorkflowRunStoreNotifying`.** Its job moves to `WorkflowRunStorePersister`.

### `Agentican.Builder` changes

- New: `.eventBus(AgenticanEventBus)` — optional override; default is a new instance per Agentican.
- New: `.addListener(AgenticanEventListener)` — replaces `.workflowRunListener(single)`.
- **Removed**: `.workflowRunListener(single)` — no backcompat (per "no backcompat in early dev" rule).
- `Builder.build()` wires:
  1. Construct the bus.
  2. Subscribe `WorkflowRunStorePersister(workflowRunStore)` via `subscribeFirst`.
  3. If default LLM exists, subscribe `KnowledgeIngestor`.
  4. Subscribe all `addListener(...)` listeners in order.
  5. Pass the bus to the engine/host so publishers can find it.

### Quarkus changes

- `AgenticanProducer.agentican()`: replace the `Instance<WorkflowRunListener>` composite with `Instance<AgenticanEventListener>` calling `builder.addListener(...)` per discovered listener.
- `MeteredTurnListener`, `TracedLifecycleListener`, `CdiEventBridge`: change implementation from `WorkflowRunListener` (many methods) to `AgenticanEventListener` (one `on(AgenticanEvent)` method with `switch (event) { case ToolCallCompleted t -> ... }`).
- `quarkus-store-jpa`: no change. `JpaWfRunStore` is still picked up as `WorkflowRunStore` and the persister wraps it.

### Temporal changes

- `TemporalAgentLoopHost` constructor accepts an `AgenticanEventBus` (per-workflow instance) instead of (or in addition to) the activity stubs.
- A `TemporalStoreActivityListener` translates events to `storeActivity.*` calls. Registered as `subscribeFirst` on the per-workflow bus.
- `TemporalAgentican.of(agentican)` constructs and pre-registers this listener under the hood.

---

## Phased plan

Each phase compiles and passes existing tests independently. No "halfway state" between phases.

### Phase 0 — Foundation (no behavior change)

Add the new types alongside the existing world. Nothing wired yet.

- [ ] Define `AgenticanEvent` sealed interface in `core/.../event/`
- [ ] Define ~22 event records covering today's `WorkflowRunListener` methods (drop the dead ones — see "Dead events" above)
- [ ] Define `AgenticanEventListener` SAM
- [ ] Define `AgenticanEventBus` with `publish/subscribe/subscribeFirst`
- [ ] Unit tests for the bus (ordering, fan-out, exception isolation — one listener throwing shouldn't break others; log + continue)
- [ ] Verify build: `mvn -pl core test`

### Phase 1 — Persister + wiring in core, behavior preserved

Inversion happens here. End state: events are first-class, store is a listener, no change to observable behavior.

- [ ] Add `WorkflowRunStorePersister implements AgenticanEventListener` — pattern-match on event type, call corresponding `WorkflowRunStore` method
- [ ] Update `Agentican.Builder`:
  - Remove `.workflowRunListener(single)`
  - Add `.addListener(AgenticanEventListener)` and `.eventBus(AgenticanEventBus)`
  - In `build()`: construct bus, subscribeFirst the persister, subscribe KnowledgeIngestor (refactored to AgenticanEventListener), subscribe user listeners
- [ ] Refactor `KnowledgeIngestor` to implement `AgenticanEventListener` instead of `WorkflowRunListener`. Pattern-match on `StepCompleted`.
- [ ] Pass the bus to `WorkflowEngine`. Update field from `workflowRunListener` to `eventBus`.
- [ ] Refactor `InProcessAgentLoopHost`: replace `store.X(...)` calls with `bus.publish(new X(...))`. Keep the store field only for the `loadRunLog` read path.
- [ ] Refactor `WorkflowRunner` write call sites (16) to publish events.
- [ ] Refactor `StepBranchRunner` (1 call site).
- [ ] Refactor `AgenticanRecovery` reap path (4 call sites).
- [ ] **Delete `WorkflowRunStoreNotifying`** and remove its imports.
- [ ] Verify build: `mvn -pl core test`. Targets: full 355-test suite green.

### Phase 2 — Quarkus integration

Quarkus listeners migrate to `AgenticanEventListener`. The hidden composite in `AgenticanProducer` simplifies into a straight subscribe loop.

- [ ] Refactor `CdiEventBridge` to implement `AgenticanEventListener` (one `switch` instead of 17 overrides). Keep the CDI `Event<T>` payloads — translate from event records to existing payload types. **Drop any `store.load()` calls — use event payload data.**
- [ ] Refactor `MeteredTurnListener` (4 overrides → 4-case switch). **Drop its `store.load()` call**; if token totals aren't in `RunCompleted`/`ResponseReceived`, enrich those events at the publish site.
- [ ] Refactor `TracedLifecycleListener` (15 overrides → switch). **Drop its `store.load()` calls**; enrich events with any attrs the listener needs.
- [ ] Refactor `TaskEventBus` (Quarkus). **Drop its `store.load()` call** for per-event broadcasts. New SSE connections still snapshot via the store — that's fine.
- [ ] Update `AgenticanProducer`:
  - Inject `Instance<AgenticanEventListener>` (was `Instance<WorkflowRunListener>`)
  - Replace 60-line inline composite with `stepListeners.forEach(builder::addListener)`
- [ ] Verify: `mvn test` across `quarkus-runtime`, `quarkus-metrics`, `quarkus-otel`, `quarkus-store-jpa`, `quarkus-integration-tests`.

### Phase 3 — Temporal adapter

- [ ] Add `TemporalStoreActivityListener implements AgenticanEventListener` in `temporal/` — pattern-match events, call `storeActivity.*`
- [ ] Update `TemporalAgentLoopHost` to take an `AgenticanEventBus`, publish to it (replacing direct `storeActivity.*` calls)
- [ ] `TemporalAgentican.of(agentican)` constructs the per-host bus and pre-registers the `TemporalStoreActivityListener`
- [ ] Verify: `mvn -pl temporal test` (E2E test must still pass)

### Phase 4 — Cleanup

- [ ] Audit currently-unused event types (`onToken`, `onStepResumed`, `onRunResumed`, `onTurnResumed`) — keep them as events for now per decision; document in this plan as candidates for follow-up review.
- [ ] Delete `WorkflowRunListener` interface (now superseded by `AgenticanEventListener`)
- [ ] Update docs: `docs/observability.md`, `docs/quarkus/extension-points.md` to describe the bus + listener pattern
- [ ] Update README and Temporal doc to mention the bus where relevant
- [ ] Verify full multi-module build: `mvn install`

---

## Open decisions to resolve before Phase 0

1. **Event naming convention**: `TaskStarted` vs `TaskStartedEvent` vs `OnTaskStarted`. Recommend bare name (no suffix) — record types named for the moment they describe. Confirm.
2. **Listener exception policy**: a listener throws — do we (a) log and continue, (b) abort the publish, (c) make it configurable? Recommend (a) — observers shouldn't break the runner. Persister exceptions are more delicate; for those, propagate so the caller knows persistence failed.
3. **Event ordering across listeners**: confirmed in plan above (persister first via `subscribeFirst`, observers after). Anyone else need ordering hooks? Or just first/rest is enough?
4. **Synchronous-only for now**: confirm we *don't* add an async dispatch in this PR. Anyone wanting async wraps a queueing listener. Easier to reason about.
5. **`AgenticanEventListener` signature**: single `void on(AgenticanEvent event)` (pattern-match), or also offer convenience methods like `default void on(TaskStarted e) {}` to avoid the switch? Recommend single-method only — keeps the contract tight; convenience methods invite forgetting cases.

---

## Out of scope for this refactor

- Replacing `WorkflowRunStore` with an event-sourced store (i.e. derived purely from event log). Worth considering separately; the persister design doesn't preclude it.
- Async/batched event dispatch.
- Persisting the event log itself (event sourcing).
- Cross-process event bus (use Temporal or another transport — bus stays in-JVM).
- Replacing the `AgentLoopHost` SPI. The host stays; only its body changes (publish instead of store).

---

## Estimated effort

- **Phase 0**: 1 session — new types + bus + tests.
- **Phase 1**: 2 sessions — touches many call sites in core; high test surface but mechanical.
- **Phase 2**: 1 session — Quarkus listener refactors are 1:1 mapping.
- **Phase 3**: 1 session — Temporal is small surface but needs E2E re-verify.
- **Phase 4**: 0.5 session — cleanup + docs.

Total: ~5 focused sessions. Each phase is checkpoint-able and reviewable.
