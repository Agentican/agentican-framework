# Agentican Quarkus

> Production-ready Quarkus integration for the Agentican agent framework.

[![Quarkus](https://img.shields.io/badge/Quarkus-3.31-blue.svg)](https://quarkus.io/)
[![Java](https://img.shields.io/badge/Java-25-orange.svg)](https://openjdk.org/projects/jdk/25/)

Drop-in Quarkus modules that turn Agentican into a fully-featured platform: REST API, real-time SSE and WebSocket streaming, human-in-the-loop via HTTP, Micrometer metrics, OpenTelemetry tracing, cron scheduling, and a Dev UI dashboard — all configurable from `application.properties`.

## Quick Start

```xml
<dependency>
    <groupId>ai.agentican</groupId>
    <artifactId>agentican-quarkus-runtime</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```

```properties
agentican.llm[0].api-key=${ANTHROPIC_API_KEY}
agentican.llm[0].model=claude-sonnet-4-5

agentican.agents[0].name=researcher
agentican.agents[0].role=Expert at finding and synthesizing information
agentican.agents[0].external-id=researcher
```

> **External IDs are required** for every agent and skill declared in config.
> They're the stable key that maps config-declared entities to persisted rows
> (see the store-jpa module). Agentican throws `IllegalStateException` at boot
> if one is missing.

```java
@Inject Agentican agentican;

var handle = agentican.run("Find papers on agent frameworks");
var result = handle.result();
```

That's it. The framework plans, executes, and returns — with CDI lifecycle, health checks, and config validation wired automatically.

## Modules

Pick the modules you need. Each is opt-in via a Maven dependency.

| Module | Artifact | What it adds |
|---|---|---|
| **CDI Core** | `agentican-quarkus` | `@Inject Agentican`, config, events, health checks |
| **Deployment** | `agentican-quarkus-deployment` | Bean discovery, native image hints, Dev UI |
| **REST** | `agentican-quarkus-rest` | REST API, SSE streaming, WebSocket, HITL bridge |
| **Metrics** | `agentican-quarkus-metrics` | Micrometer counters/timers at `/q/metrics` |
| **Tracing** | `agentican-quarkus-otel` | OpenTelemetry spans with Gen AI attributes |
| **JPA Store** | `agentican-quarkus-store-jpa` | Postgres-backed task state, knowledge, agent/skill/plan registries (Flyway V1) |
| **JPA OTel Store** | `agentican-quarkus-otel-store-jpa` | Postgres-backed span storage (Flyway V2) |
| **Scheduler** | `agentican-quarkus-scheduler` | Cron-scheduled agent tasks |
| **Test** | `agentican-quarkus-test` | Shared `MockLlmClient` + `TestTaskBuilder` |

All modules compose correctly when combined — decorators and listeners stack automatically.

## What's in the box

- **`@Inject Agentican`** — singleton CDI bean with full lifecycle management
- **`@AgenticanAgent("name")`** — inject individual agents by name
- **`ReactiveAgentican`** — Mutiny `Uni`-based API for reactive/Vert.x applications
- **REST API** — 18 endpoints for tasks, agents, checkpoints, knowledge
- **SSE streaming** — real-time events with replay via `Last-Event-ID`
- **WebSocket** — bidirectional task submission, event streaming, and HITL responses
- **HITL bridge** — respond to checkpoints over HTTP/WS; parked virtual threads wake up instantly
- **Micrometer metrics** — LLM tokens, tool calls, task lifecycle, HITL gauges
- **OpenTelemetry tracing** — step → run → turn → LLM call span hierarchy with Gen AI attributes
- **Cron scheduling** — config-driven periodic tasks
- **Dev UI** — agents, tasks, checkpoints, knowledge dashboard at `/q/dev-ui`
- **Health checks** — liveness + readiness at `/q/health`
- **Bean validation** — fail-fast on invalid config
- **Native image hints** — reflection registration for GraalVM

## Documentation

- [Getting Started](docs/getting-started.md) — install, configure, run your first task on Quarkus
- [CDI Integration](docs/cdi.md) — injection, qualifiers, events, bean overrides, reactive support
- [REST & Real-Time](docs/rest.md) — REST API, SSE streaming, WebSocket, HITL bridge
- [Observability](docs/observability.md) — Micrometer metrics, OpenTelemetry tracing, Dev UI
- [Extension Points](docs/extension-points.md) — decorators, listeners, custom executors
- [Scheduling](docs/scheduling.md) — cron-driven agent tasks
- [Testing](docs/testing.md) — MockLlmClient, TestTaskBuilder, @QuarkusTest patterns
- [Configuration Reference](docs/configuration.md) — all properties, metrics, spans, endpoints
- [JPA Store](../store-jpa/README.md) — Postgres persistence for tasks, knowledge, and registries
- [JPA OTel Store](../otel-store-jpa/README.md) — persistent span storage

## Example: Full Stack

```xml
<!-- All modules for a production deployment -->
<dependency>
    <groupId>ai.agentican</groupId>
    <artifactId>agentican-quarkus-deployment</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
<dependency>
    <groupId>ai.agentican</groupId>
    <artifactId>agentican-quarkus-rest</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
<dependency>
    <groupId>ai.agentican</groupId>
    <artifactId>agentican-quarkus-metrics</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
<dependency>
    <groupId>ai.agentican</groupId>
    <artifactId>agentican-quarkus-otel</artifactId>
    <version>1.0-SNAPSHOT</version>
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
