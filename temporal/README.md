# Agentican Temporal

Temporal.io integration for the Agentican framework. Run your Agentican agents and plans on a Temporal cluster — get durability, retries, history replay, and signal/query primitives without giving up the Agentican catalog model (agents, skills, plans, knowledge, tools).

## Installation

```xml
<dependency>
    <groupId>ai.agentican</groupId>
    <artifactId>agentican-temporal</artifactId>
    <version>0.1.0-alpha.1</version>
</dependency>
```

Requires Java 21+. Brings `io.temporal:temporal-sdk` transitively.

## What's here

- **`TemporalAgentican`** — adapter that wires an `Agentican` instance into Temporal activity implementations. Factory methods for each activity, plus a workflow-input helper that looks up plans by name.
- **`AgenticanWorkflow`** + **`AgenticanWorkflowImpl`** — generic plan interpreter. Executes any `WorkflowDefinition` loaded from YAML. One Temporal activity per agent step (coarse).
- **`FineGrainedAgenticanWorkflowImpl`** — same interface, fine-grained variant. Each agent step dispatches a child `RunnerBasedAgentWorkflow` so every LLM call and tool call is its own activity.
- **`RunnerBasedAgentWorkflow`** + **`RunnerBasedAgentWorkflowImpl`** — single-agent workflow driven by `SmacAgentRunner` via `TemporalAgentLoopHost`. Reusable as a child workflow under `FineGrainedAgenticanWorkflowImpl`, or directly for one-shot agent invocations.
- **Activities** (`ai.agentican.temporal.activity`):
  - `AgentStepActivity` / `CodeStepActivity` — coarse: one activity per plan step.
  - `LlmCallActivity` / `ToolCallActivity` — fine-grained: one activity per LLM round-trip / tool call.
  - **`AgenticanActivity`** — the bridge that funnels workflow-side `AgenticanEvent`s onto `Agentican.eventBus()` so the in-process listener set (persister, knowledge ingestor, metrics, OTel, custom hooks) handles them identically to in-process events. Also exposes `loadRunLog` for resume reads.
  - `KnowledgeStoreActivity` — runner reads knowledge entries via this.
  - `AgentConfigActivity` — parent `FineGrainedAgenticanWorkflowImpl` resolves agent configs for child workflows.
- **`TemporalAwareHitlResponseDispatcher`** (`ai.agentican.temporal.hitl`) — composite `HitlResponseDispatcher` that routes HITL responses for `TEMPORAL`-owned tasks via Temporal workflow signals and falls back to the in-process `HitlManager` for everything else. Wire it as an `@Alternative` CDI bean in your Quarkus app — see [docs/hitl.md](../docs/hitl.md#routing-responses-hitlresponsedispatcher).

## Minimal worker setup

```java
try (var agentican = Agentican.builder()
        .configuration().yaml().path(enginePath).end()
        .registry().yaml().path(catalogPath).end()
        .build()) {

    var temporal = TemporalAgentican.of(agentican);

    var worker = factory.newWorker("my-task-queue");

    worker.registerWorkflowImplementationTypes(AgenticanWorkflowImpl.class);

    worker.registerActivitiesImplementations(
            temporal.agentStepActivity());

    factory.start();
}
```

That's it. Agents and toolkits resolve through `agentican.registry()`, the LLM client through `agentican.llm(name)`. Stores default to in-memory; pass JPA-backed ones via the explicit `TemporalAgentican` constructor when you need persistence.

## Documentation

- [Temporal Integration Guide](../docs/temporal.md) — full reference with the four integration modes, the `TemporalAgentican` API, each activity, workflow classes, the `TestWorkflowEnvironment` testing pattern, and known limitations.
- Working examples in [`examples/.../temporal/`](../examples/src/main/java/ai/agentican/framework/examples/temporal/) — the same `market-brief` workflow run both coarse and fine-grained.
