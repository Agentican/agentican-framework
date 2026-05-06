# Agentican Quarkus Runtime

> CDI runtime for Agentican — `@Inject Agentican` and configure from `application.properties`.

[![Quarkus](https://img.shields.io/badge/Quarkus-3.31-blue.svg)](https://quarkus.io/)
[![Java](https://img.shields.io/badge/Java-25-orange.svg)](https://openjdk.org/projects/jdk/25/)

The runtime half of the Agentican Quarkus extension. Exposes `Agentican` as a CDI singleton, produces `EngineConfig` and `CatalogConfig` from typed `application.properties` and optional YAML, validates config at boot, wires lifecycle events, and serves liveness/readiness health checks. This is the foundation every other `quarkus-*` module composes on top of.

## Quick Start

```xml
<dependency>
    <groupId>ai.agentican</groupId>
    <artifactId>agentican-quarkus-runtime</artifactId>
    <version>0.1.0-alpha.1</version>
</dependency>
```

```properties
agentican.llm[0].api-key=${ANTHROPIC_API_KEY}
agentican.llm[0].model=claude-sonnet-4-5

agentican.agents[0].name=researcher
agentican.agents[0].role=Expert at finding and synthesizing information
```

Agents, skills, and workflows are identified by **name** within their respective registries. Persistent stores upsert by name on each boot.

```java
@Inject Agentican agentican;

var run = agentican.run("Find papers on agent frameworks");
var output = run.await();   // blocks; returns the typed String output
```

That's it. The framework plans, executes, and returns — with CDI lifecycle, health checks, and config validation wired automatically.

## What's in the box

- **`@Inject Agentican`** — singleton CDI bean with full lifecycle management.
- **`@Inject AgenticanRecovery`** — server-side recovery surface (`resumeInterrupted`, `reapOrphans`); produced from the injected `Agentican` and disposed alongside it.
- **`@AgenticanAgent("name")`** — qualifier for injecting individual agents by name.
- **`@AgenticanWorkflow(name = "...")`** — qualifier for injecting typed `Workflow<P, R>` handles bound to registered workflows.
- **`@AgenticanTask(name, agent, instructions, ...)`** — qualifier for one-shot single-step `Workflow<P, R>` injections without a pre-registered definition.
- **`ReactiveAgentican`** — Mutiny `Uni`-based API for reactive / Vert.x callers.
- **Config binding** — `EngineConfig` (engine wiring) and `CatalogConfig` (catalog seeds) produced as CDI beans. Engine config merges typed `agentican.*` properties with an optional `agentican.engine.yaml` (YAML wins). Catalog config loads from `agentican.catalog.yaml` or the JPA registries depending on `agentican.catalog-source`.
- **Lifecycle events** — `StartupEvent` / `ShutdownEvent` observers drive `Agentican` construction and `AutoCloseable` teardown.
- **Resume on start** — `ResumeOnStartObserver` invokes `AgenticanRecovery.resumeInterrupted` on `StartupEvent` to pick up tasks left in flight after a restart (toggleable via `agentican.resume-on-start`).
- **Health checks** — liveness + readiness at `/q/health`.
- **CDI event bridge** — task / step / HITL lifecycle events published to the CDI bus so other modules can observe them.
- **Native image hints** — reflection registration is contributed by the sibling [`quarkus-deployment`](../quarkus-deployment/) module.

For REST endpoints, metrics, tracing, persistence, scheduling, and test fixtures, add the matching peer module — see the [top-level module index](../README.md#modules).

## Documentation

- [Getting Started (Quarkus)](../docs/quarkus/getting-started.md) — install, configure, run your first task on Quarkus
- [CDI Integration](../docs/quarkus/cdi.md) — injection, qualifiers, events, bean overrides, reactive support
- [REST & Real-Time](../docs/quarkus/rest.md) — REST API, SSE streaming, WebSocket, HITL bridge
- [Observability](../docs/quarkus/observability.md) — Micrometer metrics, OpenTelemetry tracing, Dev UI
- [Extension Points](../docs/quarkus/extension-points.md) — decorators, listeners, custom executors
- [Scheduling](../docs/quarkus/scheduling.md) — cron-driven agent tasks
- [Testing](../docs/quarkus/testing.md) — MockLlmClient, TestTaskBuilder, @QuarkusTest patterns
- [Configuration Reference](../docs/quarkus/configuration.md) — all properties, metrics, spans, endpoints
- [JPA Store](../quarkus-store-jpa/README.md) — Postgres persistence for tasks, knowledge, and registries
- [JPA OTel Store](../quarkus-otel-store-jpa/README.md) — persistent span storage

## Example: Full Stack

```xml
<!-- All modules for a production deployment -->
<dependency>
    <groupId>ai.agentican</groupId>
    <artifactId>agentican-quarkus-deployment</artifactId>
    <version>0.1.0-alpha.1</version>
</dependency>
<dependency>
    <groupId>ai.agentican</groupId>
    <artifactId>agentican-quarkus-rest</artifactId>
    <version>0.1.0-alpha.1</version>
</dependency>
<dependency>
    <groupId>ai.agentican</groupId>
    <artifactId>agentican-quarkus-metrics</artifactId>
    <version>0.1.0-alpha.1</version>
</dependency>
<dependency>
    <groupId>ai.agentican</groupId>
    <artifactId>agentican-quarkus-otel</artifactId>
    <version>0.1.0-alpha.1</version>
</dependency>
```

```properties
agentican.llm[0].api-key=${ANTHROPIC_API_KEY}
agentican.llm[0].model=claude-sonnet-4-5

agentican.agents[0].name=researcher
agentican.agents[0].role=Expert at finding and synthesizing information

quarkus.otel.exporter.otlp.endpoint=http://localhost:4317
```

Submit a task, stream events, approve checkpoints, and monitor — all via HTTP:

```bash
# Submit
curl -X POST http://localhost:8080/agentican/tasks \
  -H "content-type: application/json" \
  -d '{"description": "Research agent frameworks and summarize findings"}'

# Stream events
curl -N http://localhost:8080/agentican/tasks/{taskId}/stream

# Approve a checkpoint
curl -X POST http://localhost:8080/agentican/checkpoints/{id}/respond \
  -H "content-type: application/json" \
  -d '{"approved": true}'

# Check metrics
curl http://localhost:8080/q/metrics | grep agentican
```

## Status

Under active development alongside the core framework. APIs are stabilizing.
