# Agentican Framework

> A multi-agent orchestration framework for Java — declarative plans with parallel steps, loops, branches, typed code steps, and human checkpoints, composed from agents, skills, tools and knowledge.

[![Maven Central](https://img.shields.io/maven-central/v/ai.agentican/agentican-framework-core.svg?label=Maven%20Central)](https://central.sonatype.com/namespace/ai.agentican)
[![Java](https://img.shields.io/badge/Java-25-orange.svg)](https://openjdk.org/projects/jdk/25/)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)

Describe a task in natural language and the built-in Planner breaks it into a structured workflow — creating agents and skills if none exist — then executes it with parallel dispatch, HITL checkpoints and durable state. Or hand-author plans with the fluent builder API and wire them into Quarkus, Spring Boot, or standalone Java.

## Quick start

```java
var config = RuntimeConfig.builder()
    .llm(LlmConfig.builder().apiKey(System.getenv("ANTHROPIC_API_KEY")).build())
    .build();

try (var agentican = Agentican.builder().config(config).build()) {

    var task = agentican.run("Research the top 5 CDC tools and compare them");
    System.out.println(task.result().output());
}
```

No agents registered. No skills defined. The Planner creates everything from scratch.

## Modules

This is a multi-module project. Each module has a focused responsibility:

### Core

| Module | ArtifactId | Description |
|---|---|---|
| [core](core/) | `agentican-framework-core` | The framework itself — agents, plans, skills, toolkits, knowledge, HITL, state, planner. Plain Java, no framework dependencies. |

### Quarkus integration

| Module | ArtifactId | Description |
|---|---|---|
| [quarkus-runtime](quarkus-runtime/) | `agentican-quarkus-runtime` | CDI bindings — `@Inject Agentican`, `application.properties` config, CDI lifecycle events, health checks. |
| [quarkus-deployment](quarkus-deployment/) | `agentican-quarkus-deployment` | Quarkus build-time processing for the extension. |
| [quarkus-rest](quarkus-rest/) | `agentican-quarkus-rest` | REST API + SSE streaming — 18 endpoints for tasks, checkpoints, knowledge, agents. |
| [quarkus-metrics](quarkus-metrics/) | `agentican-quarkus-metrics` | Micrometer metrics — tasks active/completed/duration, steps, HITL checkpoints, resume outcomes. |
| [quarkus-otel](quarkus-otel/) | `agentican-quarkus-otel` | OpenTelemetry tracing — nested spans for task → step → run → turn → llm.call / tool.call. |
| [quarkus-scheduler](quarkus-scheduler/) | `agentican-quarkus-scheduler` | Cron-scheduled task execution via `application.properties`. |
| [quarkus-store-jpa](quarkus-store-jpa/) | `agentican-quarkus-store-jpa` | JPA/Postgres persistence — agents, skills, plans, task execution state, knowledge. Flyway schema. |
| [quarkus-otel-store-jpa](quarkus-otel-store-jpa/) | `agentican-quarkus-otel-store-jpa` | Persistent OTel span storage in Postgres. |
| [quarkus-test](quarkus-test/) | `agentican-quarkus-test` | Shared test utilities for Quarkus modules. |
| [quarkus-integration-tests](quarkus-integration-tests/) | `agentican-quarkus-integration-tests` | Cross-module integration tests. |

### Applications

| Module | ArtifactId | Description |
|---|---|---|
| [server](server/) | `agentican-server` | Interactive web UI for exploring framework features. Not for production — designed to help developers learn what's possible. |
| [examples](examples/) | `agentican-framework-examples` | Real-world showcase examples spanning 9 domains from simple to complex. |

## Installation

All artifacts are published to [Maven Central](https://central.sonatype.com/namespace/ai.agentican) under the `ai.agentican` namespace — no extra repository configuration required.

**Requirements:** Java 25+

For the core framework (plain Java, no Quarkus):

```xml
<dependency>
    <groupId>ai.agentican</groupId>
    <artifactId>agentican-framework-core</artifactId>
    <version>0.1.0-alpha.1</version>
</dependency>
```

For Quarkus integration (adds CDI, REST, persistence, metrics, tracing):

```xml
<dependency>
    <groupId>ai.agentican</groupId>
    <artifactId>agentican-quarkus-runtime</artifactId>
    <version>0.1.0-alpha.1</version>
</dependency>

<!-- Pick the modules you need -->
<dependency>
    <groupId>ai.agentican</groupId>
    <artifactId>agentican-quarkus-rest</artifactId>
    <version>0.1.0-alpha.1</version>
</dependency>
<dependency>
    <groupId>ai.agentican</groupId>
    <artifactId>agentican-quarkus-store-jpa</artifactId>
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

## What's in the box

### Core framework (`agentican-framework-core`)

- **`Agentican`** — main entry point with builder API
- **`Plan` / `PlanStep`** — declarative workflow model (agent steps, loops, branches, typed code steps)
- **`PlanConfig`** — fluent builder for plans with `step()`, `loop()`, `branch()`, `codeStep()` sub-builders
- **`CodeStep<I, O>` / `CodeStepSpec<I, O>`** — register typed Java functions as plan steps (no LLM round-trip)
- **`Agent` / `AgentRunner`** — agent abstraction with pluggable runners
- **`SmacAgentRunner`** — production agent loop with tool calling, HITL and knowledge
- **`PlannerAgent`** — LLM planner that creates agents, skills and plans from natural language
- **`Toolkit`** — pluggable tool provider interface
- **`HitlManager`** — checkpoint-based human-in-the-loop (tool approval, step approval, questions)
- **`KnowledgeStore`** — persistent facts with LLM-driven extraction and `RECALL_KNOWLEDGE` tool
- **`TaskStateStore`** — durable execution state (task → step → run → turn → tool call)
- **LLM providers**: Anthropic Claude, OpenAI (Responses API), Groq, Google Gemini, AWS Bedrock (Converse API — Claude / Llama / Nova / Mistral / Cohere / DeepSeek / AI21), SambaNova, Together, Fireworks, plus an `openai-compatible` escape hatch for Ollama / vLLM / LiteLLM / corporate proxies
- **Tool integrations**: Composio (100+ SaaS), Model Context Protocol (MCP)

### Quarkus integration

- **CDI** — `@Inject Agentican`, config via `application.properties`, lifecycle events
- **REST** — 18 endpoints for task management, HITL checkpoints, knowledge and agents
- **SSE** — real-time event streaming with `Last-Event-ID` replay
- **Persistence** — JPA/Postgres with Flyway migrations for all framework stores
- **Metrics** — Micrometer counters, gauges and timers for tasks, steps, HITL, LLM usage
- **Tracing** — OpenTelemetry spans nested to mirror the execution hierarchy
- **Scheduling** — cron-scheduled tasks via config
- **Resume** — automatic task recovery after server restart at turn-boundary granularity

### Server

- Interactive web UI with task panel, step viewer, tool call inspector, HITL approval buttons
- Real-time SSE event stream
- Persistence via JPA/Postgres (survives restarts)
- All quarkus modules pre-wired

## Documentation

### Core framework

- [Getting Started](docs/getting-started.md) — install, configure and run your first task
- [Core Concepts](docs/concepts.md) — architecture, terminology, data flow
- [Plans & Steps](docs/tasks.md) — workflow modeling with agents, loops, branches, and typed code steps
- [Agents](docs/agents.md) — defining agents, skills and roles
- [Tools & Toolkits](docs/tools.md) — built-in toolkits, Composio, MCP, custom tools
- [Human in the Loop](docs/hitl.md) — approvals, questions and resumption
- [Knowledge](docs/knowledge.md) — persistent agent knowledge with facts and recall
- [Execution State](docs/execution.md) — TaskLog hierarchy, TaskStateStore, querying results
- [Observability](docs/observability.md) — TaskListener events, TaskDecorator, context propagation
- [Configuration](docs/configuration.md) — runtime config reference
- [Examples](docs/examples.md) — common patterns and recipes

### Quarkus integration

- [Getting Started (Quarkus)](docs/quarkus/getting-started.md) — dependency setup, config, first task
- [CDI Integration](docs/quarkus/cdi.md) — injection, qualifiers, lifecycle events, bean overrides
- [REST API](docs/quarkus/rest.md) — endpoints, SSE streaming, WebSocket, error codes
- [Observability](docs/quarkus/observability.md) — Micrometer metrics, OTel tracing, Prometheus queries
- [Extension Points](docs/quarkus/extension-points.md) — decorators, listeners, custom executors
- [Scheduling](docs/quarkus/scheduling.md) — cron-scheduled tasks
- [Testing](docs/quarkus/testing.md) — MockLlmClient, TestTaskBuilder, CDI observer testing
- [Configuration](docs/quarkus/configuration.md) — complete property reference

### Module-specific

- [JPA Store](quarkus-store-jpa/README.md) — persistence setup, schema, external IDs, resume semantics
- [REST API](quarkus-rest/README.md) — endpoint reference, SSE, WebSocket, CORS
- [Metrics](quarkus-metrics/README.md) — metric reference, Prometheus queries
- [OTel Tracing](quarkus-otel/README.md) — span hierarchy, attributes, Jaeger/Tempo setup
- [OTel Store](quarkus-otel-store-jpa/README.md) — persistent span storage, queries
- [Server](server/README.md) — playground server, web UI, running instructions
- [Examples](examples/README.md) — 9 showcase examples with feature matrix

## Building

```bash
# Build everything (skip tests)
cd agentican-framework
mvn clean install -DskipTests

# Run all tests
mvn test

# Build a specific module
mvn compile -pl core
mvn test -pl quarkus-store-jpa

# Run the server
mvn quarkus:dev -pl server
```

## Status

Agentican is under active development. The core APIs are stabilizing but may change before 1.0. Pin to a specific version in production.

## License

Apache 2.0 — see [LICENSE](LICENSE) for details.

## Contributing

Issues and pull requests welcome. See [CONTRIBUTING.md](CONTRIBUTING.md) for guidelines.
