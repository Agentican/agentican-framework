# Core Concepts

This page explains the core abstractions in Agentican and how they fit together.

## Architecture Overview

```
┌────────────────────────────────────────────────────────────┐
│                          Agentican                          │
└───┬──────────────┬──────────────┬────────────┬─────────────┘
    │              │              │            │
    ▼              ▼              ▼            ▼
┌──────────┐ ┌────────────┐ ┌────────────┐ ┌────────────────┐
│Workflow  │ │WorkflowRunner│ │AgenticanRegistry│ │WorkflowRunStore│
│Planner   │ │              │ │ (Agent /        │ │                │
│Agent     │ │              │ │  Skill /        │ │                │
│          │ │              │ │  Workflow)      │ │                │
└────┬─────┘ └─────┬──────┘ └─────┬──────┘ └────────────────┘
     │             │              │
     │             ▼              │
     │   ┌─────────────────────┐  │
     │   │ StepRunners         │  │
     │   │ Agent/Loop/Branch/  │  │
     │   │ Code                │  │
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

The main entry point. Owns the runtime configuration, registries, planner, and workflow runner. Build it with `Agentican.builder()` and use it via `run(String description)`, `workflow(...)`, or `task(...)`.

```java
try (var agentican = Agentican.builder()
        .configuration().api()
            .llm(LlmConfig.builder().apiKey(apiKey).build())
            .end()
        .build()) {

    var run = agentican.run("Do something useful");

    var output = run.await();
}
```

`Agentican` is `AutoCloseable` — close it to release the virtual thread executor and any toolkits that hold resources.

### Workflow&lt;P, R&gt;

A typed, reusable handle bound to a specific `WorkflowDefinition`. `Workflow<P, R>` is the dev-facing handle: you hand it a typed params record `P`, it runs the bound workflow, and (optionally) deserializes the workflow's `outputStep` output into a typed `R`.

```java
record TriageParams(String customerId, String priority) {}
record TriageOutput(String classification, String reason) {}

// Plain Java — capture a WorkflowDefinition reference
Workflow<TriageParams, TriageOutput> triage =
        agentican.workflow(definition)
                .input(TriageParams.class)
                .output(TriageOutput.class)
                .build();

// Or resolve by workflow name (must already be registered)
Workflow<TriageParams, TriageOutput> triage =
        agentican.workflow("triage")
                .input(TriageParams.class)
                .output(TriageOutput.class)
                .build();

// Run with typed in + typed out
TriageOutput out = triage.start(new TriageParams("cust-42", "HIGH")).await();

// Or get the full execution struct (status, step outputs, token usage)
WorkflowRunResult result = triage.start(new TriageParams("cust-42", "HIGH")).untypedResult();
```

Use `Void` for either type parameter when no inputs or no typed output is needed:

- `Workflow<P, Void>` — typed inputs, untyped output. `start(params).untypedResult()` returns the raw `WorkflowRunResult`.
- `Workflow<Void, R>` — parameterless workflow, typed output. `start().await()` (no args) parses the output.
- `Workflow<Void, Void>` — both untyped.

Two factory forms on `Agentican`:

- **`agentican.workflow(WorkflowDefinition)`** — captures the definition reference directly. No registry lookup.
- **`agentican.workflow(String name)`** — resolves by name in the `WorkflowRegistry` at `build()` time. Throws if the name isn't registered.

Chain `.input(P.class)` (required), then `.output(R.class)` (optional; defaults to `Void`), then `.build()` to get `Workflow<P, R>`. The output step and structured-output binding are computed once in the `Workflow` constructor — `start()` is just dispatch.

**Params** are converted via Jackson with `SNAKE_CASE` naming, so `TriageParams.customerId` maps to plan param `customer_id`. Nested objects/collections JSON-serialize into strings.

**Typed output** comes from the plan's *output step*. For single-step plans, that step is implicit. For multi-step plans, declare it on the builder:

```java
WorkflowDefinition.builder("triage", "Triage")
    .outputStep("classify")     // ← which step's output IS the plan's output
    .step(...)
    .step(WorkflowStepAgent.builder("classify").agent("triage")
            .instructions("Respond with JSON: {classification, reason}")
            .build())
    .build();
```

The output step's text output is parsed via Jackson into `R` at the boundary. The framework generates a JSON Schema from `R` and attaches it to that step's LLM requests via the provider's native structured-output mode — Anthropic `output_config.format`, OpenAI `response_format: json_schema (strict)`, Gemini `responseJsonSchema`, and passthrough `response_format` for OpenAI-compatible endpoints — so the model is constrained to emit conformant JSON, not just steered toward it.

Failure modes from `await()`:
- Run didn't complete → `WorkflowOutputException` (carries the failed `WorkflowRunResult`).
- Output step produced text that doesn't match `R` → `WorkflowOutputException` (carries the raw output and target class).

In Quarkus, inject by workflow name with both type parameters:

```java
@Inject @Workflow(name = "triage")
Workflow<TriageParams, TriageOutput> triage;
```

### AgenticanRecovery

A server-oriented companion to `Agentican` that owns the recovery surface — `resumeInterrupted(...)` and `reapOrphans(...)`. Obtain one from `agentican.recovery()`:

```java
try (var agentican = Agentican.builder()...build();
     var recovery = agentican.recovery()) {

    recovery.resumeInterrupted();   // pick up tasks left in flight after restart
    // agentican.run(...) calls happen as before
}
```

`AgenticanRecovery` is `AutoCloseable` — declare it after the `Agentican` in try-with-resources so it closes first (it awaits in-flight knowledge re-ingestion using the shared executor before the executor shuts down). Each call to `agentican.recovery()` returns a fresh instance; in DI (Quarkus) the producer bean caches one per `Agentican`.

In Quarkus, `AgenticanRecovery` is produced as a singleton; the framework's `ResumeOnStartObserver` invokes `resumeInterrupted` on `StartupEvent` (toggleable via `agentican.resume-on-start`).

### WorkflowDefinition

A `WorkflowDefinition` is a structured workflow: an id, name, description, parameters, an optional output step, and a list of steps.

```java
record WorkflowDefinition(
    String id,          // required — stable identifier; recommend slug-style
    String name,        // unique within the WorkflowRegistry
    String description,
    List<WorkflowParam> params,
    List<WorkflowStep> steps,
    String outputStep   // optional — step whose output is the workflow's typed output
)
```

Construction:

```java
WorkflowDefinition.builder(id, name)
    .description(description)
    .param(...)
    .step(...)
    .outputStep("final-step")
    .build();
```

`id` is required at the call site — pick a stable slug (`triage`, `incident-postmortem`); the framework rejects null/blank ids. The same applies to `AgentConfig`, `SkillConfig`, and `WorkflowConfig`. The planner manufactures slug-style ids automatically when it materialises an agent, skill, or workflow from a natural-language description.

You can build a `WorkflowDefinition` manually with the builder, or let the planner create one from a natural-language description.

### WorkflowStep

A step in a workflow. Four variants (sealed interface):

- **`WorkflowStepAgent`** — runs an agent with given instructions
- **`WorkflowStepLoop`** — iterates over an upstream step's output, running a sub-plan per item
- **`WorkflowStepBranch`** — picks one of several paths based on an upstream step's output
- **`WorkflowStepCode<I>`** — runs a registered Java function (no LLM round-trip), with a typed input and output

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
        .workflowRunStore(taskStateStore)
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

A `WorkflowRunStore` persists execution state. `InMemoryWfRunStore` is provided; you can implement your own for durable storage.

`TaskLog`, `StepLog`, `TurnLog`, and `KnowledgeEntry` all have constructors that accept full state (timestamps, ids, status) so a persistent store can round-trip an instance without stamping fresh values on rehydrate.

### Registries

All five registries are bundled on `agentican.registry()` (an `AgenticanRegistry` record):

- **`WorkflowRegistry`** — workflows by name and id. Pre-built workflows from config + planner-generated workflows. Access via `agentican.registry().workflows()`.
- **`AgentRegistry`** — agents by id and name. Populated from config, the fluent builder, and planner-created agents. Access via `agentican.registry().agents()`.
- **`SkillRegistry`** — skills by id and name. Populated from config, the fluent builder, and planner-created skills. Access via `agentican.registry().skills()`.
- **`ToolkitRegistry`** — slug → Toolkit. Populated from MCP, Composio, custom toolkits, and built-ins. Access via `agentican.registry().toolkits()`.
- **`VectorIndexRegistry`** — name → VectorIndex. Populated from `.vectorIndex(...)` on the Builder. Access via `agentican.registry().indexes()`.

`AgentRegistry`, `SkillRegistry`, and `WorkflowRegistry` are **interfaces** with `InMemory*` implementations as the default. A persistent backend (e.g., the JPA-backed registries in `agentican-quarkus-store-jpa`) plugs in via the Builder:

```java
Agentican.builder()
        .agentRegistry(myJpaAgentRegistry)
        .skillRegistry(myJpaSkillRegistry)
        .workflowRegistry(myJpaWorkflowRegistry)
        .build();
```

Each interface has a `default seed()` hook the framework calls once at boot. `AgentRegistry.agentFactory(Function<AgentConfig, Agent>)` is set by the framework so a persistent registry can hydrate cataloged agents on `seed()`.

### Identity by name

Agents, skills, and workflows are looked up by **name** within their respective registries — names are unique per registry. The internal `id` field on each config record is auto-generated if not supplied; it's an implementation detail used by persistence stores. Plans authored programmatically, in YAML, or by the planner all reference agents and workflows by name.

## Execution Flow

When you call `agentican.run("description")`:

1. **Plan** — `WorkflowPlannerAgent.plan(String)` returns `WorkflowPlan(WorkflowDefinition definition, Map<String, String> inputs)`:
   - **Decide**: the planner prompt includes an `<existing-plans>` block listing cataloged workflows (name, description, param names). The LLM returns either a `WorkflowSelected(name, inputs)` (when a cataloged workflow fits) or a `WorkflowPlanned` (a brand-new workflow).
   - **Reuse path**: look the workflow up by name; the `inputs` map flows into dispatch.
   - **Create path**: a refinement pass then rewrites each step's instructions with the real tool schemas.
   - **Fallback**: if the planner references a workflow name that isn't in the catalog, retry once with an empty `<existing-plans>` block (forces a create).
2. **Run** — `WorkflowRunner.run(definition, taskId, inputs, cancelled)`:
   - Build dependency graph from step references
   - Validate no cycles
   - Dispatch ready steps to virtual threads
   - Poll for completion, dispatch dependents
   - Handle HITL suspension by parking on `awaitResponse()`
   - Save `WorkflowRunLog` after each step
3. **Return** — final `WorkflowRunResult` with status and per-step results, wrapped in a `WorkflowRun<String>` whose `await()` yields the workflow's last-step output.

### Recovery flow

When the server starts and a task was left in-flight (e.g., a previous JVM was killed mid-run), `AgenticanRecovery` is responsible for picking it up:

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
