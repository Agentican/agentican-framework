# Core Concepts

This page explains the core abstractions in Agentican and how they fit together.

## Architecture Overview

```
┌────────────────────────────────────────────────────────────┐
│                          Agentican                         │
└───┬──────────────┬──────────────┬────────────┬─────────────┘
    │              │              │            │
    ▼              ▼              ▼            ▼
┌──────────┐ ┌────────────┐ ┌────────────┐ ┌────────────────┐
│ Planner  │ │TaskRunner  │ │ Registries │ │TaskStateStore  │
│  Agent   │ │            │ │ (Agent /   │ │                │
│          │ │            │ │  Skill /   │ │                │
│          │ │            │ │  Plan)     │ │                │
└────┬─────┘ └─────┬──────┘ └─────┬──────┘ └────────────────┘
     │             │              │
     │             ▼              │
     │   ┌─────────────────────┐  │
     │   │ StepRunners         │  │
     │   │  Agent/Loop/Branch  │  │
     │   └──────────┬──────────┘  │
     │              ▼             │
     │   ┌─────────────────────┐  │
     │   │  SmacAgentRunner    │◄─┘   (built from AgentConfig
     │   │   (LLM loop)        │      via AgentFactory)
     │   └─────┬─────────┬─────┘
     │         ▼         ▼
     │   ┌─────────┐ ┌────────────┐
     │   │LlmClient│ │ Toolkits   │
     │   └─────────┘ └────────────┘
     ▼
┌─────────────────────────┐
│ AgentFactory (builds    │
│ agents from AgentConfig │
│ for planner + registry  │
│ seeding)                │
└─────────────────────────┘
```

## Key Concepts

### Agentican

The main entry point. Owns the runtime configuration, registries, planner, and task runner. Build it with `Agentican.builder()` and use it via `run(String)` or `run(Plan)`.

```java
try (var agentican = Agentican.builder()
        .llm(LlmConfig.builder().apiKey(apiKey).build())
        .build()) {

    var handle = agentican.run("Do something useful");

    var result = handle.result();
}
```

`Agentican` is `AutoCloseable` — close it to release the virtual thread executor and any toolkits that hold resources.

At `build()` time, the framework validates that every `AgentConfig`, `SkillConfig`, and `PlanConfig` supplied via the config file or the fluent builder declares an `externalId`. See [External IDs](#external-ids) below.

### AgenticanService

A separate, server-oriented companion to `Agentican` that owns the recovery surface — `resumeInterrupted(...)` and `reapOrphans(...)`. Construct it by composing onto an Agentican:

```java
try (var agentican = Agentican.builder()...build();
     var service = new AgenticanService(agentican)) {

    service.resumeInterrupted();   // pick up tasks left in flight after restart
    // agentican.run(...) calls happen as before
}
```

`AgenticanService` is also `AutoCloseable` — declare it after the `Agentican` in try-with-resources so it closes first (it awaits in-flight knowledge re-ingestion using the shared executor before the executor shuts down).

In Quarkus, `AgenticanService` is a CDI bean produced from the injected `Agentican`; the framework's `ResumeOnStartObserver` invokes `resumeInterrupted` on `StartupEvent` (toggleable via `agentican.resume-on-start`).

### Plan

A `Plan` is a structured workflow definition: an internal UUID, an optional `externalId` business key, name, description, parameters, and a list of steps.

```java
record Plan(
    String id,          // internal UUID, auto-generated
    String name,
    String description,
    List<PlanParam> params,
    List<PlanStep> steps,
    String externalId   // optional stable key for catalog upserts
)
```

Construction:

```java
Plan.builder(name)
    .description(description)
    .externalId(externalId)   // optional — cataloged plans only
    .param(...)
    .step(...)
    .build();
```

You can build a `Plan` manually with the builder, or let the planner create one from a natural language description.

### PlanStep

A step in a plan. Four variants (sealed interface):

- **`PlanStepAgent`** — runs an agent with given instructions
- **`PlanStepLoop`** — iterates over an upstream step's output, running a sub-plan per item
- **`PlanStepBranch`** — picks one of several paths based on an upstream step's output
- **`PlanStepCode<I>`** — runs a registered Java function (no LLM round-trip), with a typed input and output

Steps can depend on each other. The runner builds a dependency graph and executes independent steps in parallel.

### Agent

An `Agent` is a record pairing an `AgentConfig` (identity + role + LLM choice) with an `AgentRunner` (execution strategy). `id()`, `name()`, and `role()` are accessors that delegate to the config.

```java
record Agent(
    AgentConfig config,
    AgentRunner runner
)
```

Construction is builder-only:

```java
Agent.builder().config(agentConfig).runner(runner).build();
```

The `AgentRunner` is the actual execution strategy. The default is `SmacAgentRunner`, which runs the standard agent loop: send LLM request → execute returned tool calls → repeat until the LLM returns text.

### AgentFactory

`AgentFactory` turns an `AgentConfig` into a runtime `Agent`. It's a separate class wired with everything an agent needs — LLM clients, the HITL manager, the knowledge store, the task state store, the skill registry, and the task listener.

```java
var factory = AgentFactory.builder()
        .config(runtimeConfig)
        .llms(llms)
        .hitlManager(hitlManager)
        .knowledgeStore(knowledgeStore)
        .taskStateStore(taskStateStore)
        .skillRegistry(skillRegistry)
        .taskListener(taskListener)
        .build();

Agent agent = factory.build(agentConfig);
```

Used internally by:
- the config-/fluent-builder path (to materialize pre-declared agents)
- the planner (when an agent config it returned isn't in the registry)
- persistent-registry `seed(factory)` hooks (to hydrate cataloged agents at boot)

### Toolkit

A `Toolkit` is a collection of tools an agent can call. It's an interface:

```java
interface Toolkit {
    List<Tool> tools();
    boolean handles(String toolName);
    String execute(String toolName, Map<String, Object> arguments) throws Exception;
}
```

Out of the box, Agentican ships with:
- **`ScratchpadToolkit`** — agent-local key/value memory across turns
- **`AskQuestionToolkit`** — lets the agent pause and ask the user a question
- **`KnowledgeToolkit`** — exposes `RECALL_KNOWLEDGE` when a `KnowledgeStore` is configured
- **`ComposioToolkit`** — wraps Composio's 200+ SaaS integrations
- **`McpToolkit`** — wraps any Model Context Protocol server

You can register your own toolkits with the builder.

### HitlManager

Coordinates human-in-the-loop checkpoints. When an agent needs approval to call a sensitive tool, or wants to ask the user a question, it creates a checkpoint via `HitlManager`. The framework parks the virtual thread until the app responds.

```java
var hitlManager = new HitlManager((mgr, checkpoint) -> {

    // Surface the checkpoint to the user (UI, REST API, Slack, etc.)
    // When they respond, call mgr.respond(checkpoint.id(), response)
});
```

### TaskLog

A unified trace + state structure that captures everything that happens during task execution. Contains the task definition, parameters, and per-step logs with all agent runs and LLM turns.

```
TaskLog
  └── steps: Map<String, StepLog>
        └── runs: List<RunLog>      ← each retry adds a new run
              └── turns: List<TurnLog>
                    ├── request, response
                    └── toolResults
```

A `TaskStateStore` persists execution state. `MemTaskStateStore` is provided; you can implement your own for durable storage.

`TaskLog`, `StepLog`, `TurnLog`, and `KnowledgeEntry` all have constructors that accept full state (timestamps, ids, status) so a persistent store can round-trip an instance without stamping fresh values on rehydrate.

### Registries

All four registries are bundled on `agentican.registry()` (an `AgenticanRegistry` record):

- **`PlanRegistry`** — plans by name and internal id. Pre-built plans from config + planner-generated plans. Access via `agentican.registry().plans()`.
- **`AgentRegistry`** — agents by id and name. Populated from config, the fluent builder, and planner-created agents. Access via `agentican.registry().agents()`.
- **`SkillRegistry`** — skills by id and name. Populated from config, the fluent builder, and planner-created skills. Access via `agentican.registry().skills()`.
- **`ToolkitRegistry`** — slug → Toolkit. Populated from MCP, Composio, custom toolkits, and built-ins. Access via `agentican.registry().toolkits()`.

All three of `AgentRegistry`, `SkillRegistry`, and `PlanRegistry` are **interfaces** with an `InMemory*` implementation as the default. A persistent backend (e.g., the JPA-backed registries in `agentican-quarkus`) plugs in via the builder:

```java
Agentican.builder()
        .agentRegistry(myJpaAgentRegistry)
        .skillRegistry(myJpaSkillRegistry)
        .planRegistry(myJpaPlanRegistry)
        .build();
```

Each interface has a `default seed(...)` hook the framework calls once at boot. `AgentRegistry.seed(Function<AgentConfig, Agent>)` receives the `AgentFactory` so a persistent registry can hydrate cataloged agents. `SkillRegistry.seed()` and `PlanRegistry.seed()` take no args.

### External IDs

Every `AgentConfig`, `SkillConfig`, `PlanConfig`, and `Plan` has an optional `externalId` — a stable business key separate from the internal UUID `id`. A persistent catalog upserts on `externalId` so redeploys don't create duplicates.

Anything declared via the config file or the fluent builder **must** set an `externalId`. The framework throws `IllegalStateException` at `Agentican.build()` if it's missing, because without it every boot would auto-generate a fresh UUID and pile up duplicate catalog rows.

Set `externalId(...)` on the builder:

```java
AgentConfig.builder()
        .externalId("agent.researcher.v1")
        .name("researcher").role("Expert researcher").llm("default")
        .build();

SkillConfig.builder()
        .externalId("skill.citations.v1")
        .name("citations").instructions("Always cite sources")
        .build();

Plan.builder("research")
        .externalId("plan.research.v1")
        .description("...")
        .param(...).step(...)
        .build();
```

Planner-created agents, skills, and plans legitimately have no `externalId` — they're ephemeral, scoped to the run that produced them.

## Execution Flow

When you call `agentican.run("description")`:

1. **Plan** — `PlannerAgent.plan(String)` returns `PlanningResult(Plan, Map<String, String> inputs)`:
   - **Decide**: the planner prompt includes an `<existing-plans>` block listing cataloged plans (id, name, description, param names). The LLM returns either a `ReuseExisting(planRef, inputs)` (when a cataloged plan fits) or a `PlannerOutput` (a brand-new plan).
   - **Reuse path**: look the plan up by internal id; the `inputs` map flows into the task.
   - **Create path**: a refinement pass then rewrites each step's instructions with the real tool schemas.
   - **Fallback**: if the planner references a plan id that isn't in the catalog, retry once with an empty `<existing-plans>` block (forces a create).
2. **Run** — `TaskRunner.run(plan, taskId, inputs, cancelled)`:
   - Build dependency graph from step references
   - Validate no cycles
   - Dispatch ready steps to virtual threads
   - Poll for completion, dispatch dependents
   - Handle HITL suspension by parking on `awaitResponse()`
   - Save `TaskLog` after each step
3. **Return** — final `TaskResult` with status and per-step results

### Recovery flow

When the server starts and a task was left in-flight (e.g., a previous JVM was killed mid-run), `AgenticanService` is responsible for picking it up:

1. **Classify** — `ResumeClassifier.classify(taskLog, plan)` walks the persisted `TaskLog` to decide what to do: drive the in-flight step forward, or reap it if it can't be resumed (corrupt state, missing plan, etc.).
2. **Rehydrate** — pending HITL checkpoints are restored to the `HitlManager`; persisted HITL responses are replayed; completed-step outputs are re-ingested into the knowledge store.
3. **Submit** — the resumable task is handed back to `TaskRunner.resume(...)` on the same executor, gated by a configurable concurrency semaphore.
4. **Reap** — unrecoverable parents and any dangling sub-tasks are marked FAILED with a reason (`SERVER_RESTARTED`, `DANGLING_PARENT_TERMINAL`, `PARENT_REAPED`, etc.) and the listener is notified via `onTaskReaped`.

Server applications wire this in by calling `service.resumeInterrupted()` on startup; the Quarkus runtime does this automatically.

## Threading Model

Agentican uses **virtual threads exclusively** from the moment you call `run()`:

- `Agentican` owns an `Executors.newVirtualThreadPerTaskExecutor()` for task execution
- Each task step runs on its own virtual thread
- Parallel tool execution and loop iterations use `StructuredTaskScope`
- HITL waits **park** the virtual thread — no OS threads are blocked, even for hours-long human approvals

This means you can have thousands of in-flight tasks waiting on HITL without exhausting any thread pools.

## Next Steps

- [Tasks & Steps](tasks.md) — workflow modeling in depth
- [Agents](agents.md) — defining specialized agents
- [Tools & Toolkits](tools.md) — extending with custom tools
- [Human in the Loop](hitl.md) — approvals and questions
