# Agentican Framework Core

> The plain-Java multi-agent orchestration engine — no Quarkus, no Spring, no container.

[![Java](https://img.shields.io/badge/Java-25-orange.svg)](https://openjdk.org/projects/jdk/25/)

This is the framework. Everything else in this repo (`quarkus-runtime`, `quarkus-rest`, `quarkus-store-jpa`, …) is an integration layer wrapped around what's in here. If you're embedding Agentican into a plain Java app, a CLI, a Lambda, or any non-Quarkus runtime, depend on this module directly.

## Install

```xml
<dependency>
    <groupId>ai.agentican</groupId>
    <artifactId>agentican-framework-core</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

Requires Java 25.

## Quickstart

```java
var config = RuntimeConfig.builder()
    .llm(LlmConfig.builder().apiKey(System.getenv("ANTHROPIC_API_KEY")).build())
    .build();

try (var agentican = Agentican.builder().config(config).build()) {
    var task = agentican.run("Research the top 5 CDC tools and compare them");
    System.out.println(task.result().output());
}
```

No agents, skills, or plans registered — the built-in `PlannerAgent` creates them from the task description. For declarative workflows, use the `PlanConfig` builder (loops, branches, HITL checkpoints, parallel steps).

## What's in the box

Everything is under `ai.agentican.framework.*`.

**Entry point**
- `Agentican` — orchestrator, `AutoCloseable`; fluent builder for decorators, listeners, registries, and state stores.

**Orchestration model** (`orchestration`)
- `Plan`, `PlanStep`, `PlanStepAgent`, `PlanStepBranch`, `PlanStepLoop` — the declarative workflow AST.
- `PlanConfig` — fluent builder with `step()`, `loop()`, `branch()` sub-builders.
- `PlannerAgent` — LLM-driven planner that manufactures agents, skills, and plans from natural language.
- `TaskRunner`, `TaskHandle`, `TaskStatus` — execution primitives.

**Agents & skills** (`agent`, `skill`)
- `Agent`, `AgentRunner`, `SmacAgentRunner` — agent abstraction and the production tool-calling loop.
- `AgentRegistry`, `SkillRegistry` — pluggable in-memory implementations included; swap for persistent registries via the builder.

**LLM clients** (`llm`)
- `LlmClient`, `AnthropicLlmClient`, `RetryingLlmClient`, `LlmClientDecorator` — decorator chain for retries, caching, metering.

**Tools** (`tools`)
- `Toolkit` — the tool-provider interface.
- `McpToolkit` — Model Context Protocol support.
- `composio.ComposioClient` + toolkit — 100+ SaaS integrations.
- Scratchpad and built-in toolkits for framework-managed state.

**HITL** (`hitl`)
- `HitlManager`, `HitlNotifier` — checkpoint-based tool approval, step approval, and free-form questions with durable resume.

**Knowledge** (`knowledge`)
- `KnowledgeStore`, `MemKnowledgeStore`, `KnowledgeIngestor`, `LlmKnowledgeExtractor` — persistent agent facts + `RECALL_KNOWLEDGE` tool.

**State** (`state`)
- `TaskStateStore`, `MemTaskStateStore`, `NotifyingTaskStateStore` — the durable record of every task → step → run → turn → tool call.

**Config** (`config`) — plain records, all builder-based:
- `RuntimeConfig` · `LlmConfig` · `AgentConfig` · `SkillConfig` · `PlanConfig` · `McpConfig` · `ComposioConfig` · `WorkerConfig`

## Runtime characteristics

- **Virtual threads** — task execution defaults to `Executors.newVirtualThreadPerTaskExecutor()`. Parks during HITL without tying up platform threads.
- **Resumable** — `agentican.resumeInterrupted()` + `reapOrphans()` pick up tasks left in flight after a restart at turn-boundary granularity (pair with a persistent `TaskStateStore`).
- **Observable** — plug in `TaskListener`s and `TaskDecorator`s via the builder; the Quarkus metrics / OTel modules are built on exactly these hooks.

## When to reach for a peer module instead

If your app already runs on Quarkus, use [`agentican-quarkus-runtime`](../quarkus-runtime/) — it injects `Agentican` as a CDI bean, reads `RuntimeConfig` from `application.properties`, and unlocks the REST / metrics / tracing / JPA / scheduler modules. This core module is the engine underneath; you don't need to depend on it directly when using the Quarkus stack.

## Documentation

- [Getting Started](../docs/getting-started.md) — install, configure, run your first task.
- [Core Concepts](../docs/concepts.md) — architecture, terminology, data flow.
- [Plans & Steps](../docs/tasks.md) — workflow modeling with agents, loops, branches.
- [Agents](../docs/agents.md) — defining agents, skills, roles.
- [Tools & Toolkits](../docs/tools.md) — built-in toolkits, Composio, MCP, custom tools.
- [Human in the Loop](../docs/hitl.md) — approvals, questions, resumption.
- [Knowledge](../docs/knowledge.md) — persistent facts and recall.
- [Execution State](../docs/execution.md) — `TaskLog` hierarchy, state store, querying results.
- [Observability](../docs/observability.md) — listeners, decorators, context propagation.
- [Configuration](../docs/configuration.md) — runtime config reference.

## Related

- [Top-level module index](../README.md#modules).
