# Core Concepts

This page explains the core abstractions in Agentican and how they fit together.

## Architecture Overview

```
┌────────────────────────────────────────────────────────────┐
│                          Agentican                         │
└─────────┬────────────────────┬───────────────────┬─────────┘
          │                    │                   │
          ▼                    ▼                   ▼
┌────────────────────┐  ┌──────────────┐  ┌──────────────────┐
│  TaskPlannerAgent  │  │  TaskRunner  │  │  TaskStateStore  │
└────────────────────┘  └──────┬───────┘  └──────────────────┘
                               │
                               ▼
              ┌─────────────────────────────────┐
              │     StepRunners (per type)      │
              │  ┌───────┐ ┌──────┐ ┌────────┐  │
              │  │ Agent │ │ Loop │ │ Branch │  │
              │  └───┬───┘ └───┬──┘ └────┬───┘  │
              └──────┼─────────┼─────────┼──────┘
                     ▼         ▼         ▼
              ┌─────────────────────────────────┐
              │    SmacAgentRunner (LLM loop)   │
              └──────┬───────────────────┬──────┘
                     ▼                   ▼
              ┌──────────────┐   ┌──────────────┐
              │  LLM Client  │   │ Toolkits and │
              │              │   │    Tools     │
              └──────────────┘   └──────────────┘
```

## Key Concepts

### Agentican

The main entry point. Owns the runtime configuration, registries, planner, and task runner. Build it with `Agentican.builder()` and use it via `run(String)` or `run(Plan)`.

```java
try (var agentican = Agentican.builder().config(config).build()) {

    var handle = agentican.run("Do something useful");

    var result = handle.result();
}
```

`Agentican` is `AutoCloseable` — close it to release the virtual thread executor and any toolkits that hold resources.

### Plan

A `Plan` is a structured workflow definition: an auto-generated ID, name, description, parameters, and a list of steps.

```java
record Plan(String id, String name, String description, List<PlanParam> params, List<PlanStep> steps)
```

You can build a `Plan` manually with the builder, or let the planner create one from a natural language description.

### PlanStep

A step in a plan. Three variants (sealed interface):

- **`PlanStepAgent`** — runs an agent with given instructions
- **`PlanStepLoop`** — iterates over an upstream step's output, running a sub-plan per item
- **`PlanStepBranch`** — picks one of several paths based on an upstream step's output

Steps can depend on each other. The runner builds a dependency graph and executes independent steps in parallel.

### Agent

An `Agent` is a record holding a name, role, optional skills, and an `AgentRunner`.

```java
record Agent(String name, String role, List<SkillConfig> skills, AgentRunner runner)
```

The `AgentRunner` is the actual execution strategy. The default is `SmacAgentRunner`, which runs the standard agent loop: send LLM request → execute returned tool calls → repeat until the LLM returns text.

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

### Registries

- **`PlanRegistry`** — name/id → Plan. Pre-built plans from config + planner-generated plans. Access via `agentican.plans()`.
- **`AgentRegistry`** — name → Agent. Populated from config + planner-created agents. Access via `agentican.agents()`.
- **`ToolkitRegistry`** — slug → Toolkit. Populated from MCP, Composio, custom toolkits, and built-ins. Access via `agentican.toolkitRegistry()`.

The planner creates new agent configs as needed during planning; the framework builds them via the agent factory and registers them automatically.

## Execution Flow

When you call `agentican.run("description")`:

1. **Plan** — `TaskPlannerAgent.plan()`:
   - **Pass 1**: LLM creates initial task structure with agents and steps
   - **Build & register agents** that the planner introduced
   - **Pass 2**: Refine each agent step's instructions with tool context (parallel)
   - **Pass 3**: Refine loop steps with shared-context optimizations (parallel)
2. **Run** — `TaskRunner.run()`:
   - Build dependency graph from step references
   - Validate no cycles
   - Dispatch ready steps to virtual threads
   - Poll for completion, dispatch dependents
   - Handle HITL suspension by parking on `awaitResponse()`
   - Save `TaskLog` after each step
3. **Return** — final `TaskResult` with status and per-step results

## Threading Model

Agentican uses **virtual threads exclusively** from the moment you call `run()`:

- `Agentican` owns a `Executors.newVirtualThreadPerTaskExecutor()` for task execution
- Each task step runs on its own virtual thread
- Parallel tool execution and loop iterations use `StructuredTaskScope` (Java 25 preview)
- HITL waits **park** the virtual thread — no OS threads are blocked, even for hours-long human approvals

This means you can have thousands of in-flight tasks waiting on HITL without exhausting any thread pools.

## Next Steps

- [Tasks & Steps](tasks.md) — workflow modeling in depth
- [Agents](agents.md) — defining specialized agents
- [Tools & Toolkits](tools.md) — extending with custom tools
- [Human in the Loop](hitl.md) — approvals and questions
