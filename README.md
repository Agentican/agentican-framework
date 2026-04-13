# Agentican

> A lightweight Java framework for embedding tool-using LLM agents into your applications.

[![Java](https://img.shields.io/badge/Java-25-orange.svg)](https://openjdk.org/projects/jdk/25/)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![Build](https://img.shields.io/badge/build-passing-brightgreen.svg)](#)

Agentican lets Java developers add agentic capabilities to their applications with minimal ceremony. Describe a task in natural language, and an LLM-powered planner breaks it into steps, picks the right agents and tools, and executes the workflow — with human approval gates where you need them.

## Why Agentican?

- **Plan from natural language** — A 3-pass LLM planner converts user intent into a structured task with agents, steps, loops, and branches. No manual workflow definition required.
- **Built for Java** — Plain Java 25, no framework lock-in. Works alongside Spring, Quarkus, or anything else.
- **Virtual threads** — Massive concurrency without thread pool tuning. Long-running HITL waits don't tie up OS threads.
- **Tool integrations included** — Composio (200+ SaaS integrations) and MCP (Model Context Protocol) work out of the box. Add your own toolkits with a simple interface.
- **Human-in-the-loop** — Pause for approvals on sensitive tool calls or step outputs. Ask the user a question mid-workflow. Resume cleanly.
- **Multi-agent orchestration** — Compose specialized agents that collaborate via shared task state. Loops and branches let one task spawn many.
- **Persistent knowledge** — Optional `KnowledgeStore` lets agents accumulate and recall structured facts across tasks. Plug in any backing store.
- **Observability built in** — Every LLM call, tool result, and step output is captured in a unified `TaskLog`.

## Quick Start

```java
import ai.agentican.framework.Agentican;
import ai.agentican.framework.config.LlmConfig;
import ai.agentican.framework.config.RuntimeConfig;

var config = RuntimeConfig.builder()
    .llm(LlmConfig.builder().apiKey(System.getenv("ANTHROPIC_API_KEY")).build())
    .build();

try (var agentican = Agentican.builder().config(config).build()) {

    var handle = agentican.run("Find the top 3 LLMs based on reasoning. Summarize each.");

    var result = handle.result();
    System.out.println(result.lastOutput());
}
```

That's it. The planner figures out what agents and steps are needed, executes them in parallel where it can, and returns the final result.

## Installation

**Requirements:** Java 25+

```xml
<dependency>
    <groupId>ai.agentican</groupId>
    <artifactId>agentican-framework</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```

## What's in the box?

- **`Agentican`** — main entry point with builder API
- **`Plan` / `PlanStep`** — declarative workflow model (agent steps, loops, branches)
- **`Agent` / `AgentRunner`** — agent abstraction with pluggable runners
- **`SmacAgentRunner`** — production-ready agent loop with tool calling and HITL
- **`Toolkit`** — pluggable tool provider interface
- **`HitlManager`** — checkpoint-based human-in-the-loop coordination
- **`KnowledgeStore`** — persistent fact storage with `RECALL_KNOWLEDGE` tool for agents
- **`TaskLog` / `TaskStateStore`** — unified trace + state for every task run
- **Built-in toolkits**: `ScratchpadToolkit` (per-task memory), `AskQuestionToolkit` (user questions), `KnowledgeToolkit` (cross-task facts)
- **Out-of-the-box integrations**: Anthropic Claude, Composio, Model Context Protocol (MCP)

## Documentation

- [Getting Started](docs/getting-started.md) — install, configure, and run your first task
- [Core Concepts](docs/concepts.md) — architecture, terminology, data flow
- [Plans & Steps](docs/tasks.md) — workflow modeling with agents, loops, and branches
- [Agents](docs/agents.md) — defining agents, skills, and roles
- [Tools & Toolkits](docs/tools.md) — built-in toolkits, Composio, MCP, custom tools
- [Human in the Loop](docs/hitl.md) — approvals, questions, and resumption
- [Knowledge](docs/knowledge.md) — persistent agent knowledge with facts and recall
- [Execution State](docs/execution.md) — TaskLog hierarchy, TaskStateStore, querying results
- [Observability](docs/observability.md) — TaskListener events, TaskDecorator, context propagation
- [Configuration](docs/configuration.md) — runtime config reference
- [Examples](docs/examples.md) — common patterns and recipes

## Example: Custom Toolkit + HITL

```java
var notionToolkit = new MyNotionToolkit(notionApiKey);

var hitlManager = new HitlManager((mgr, checkpoint) -> {

    System.out.println("Approve? " + checkpoint.description());
    var approved = readUserInput().equals("y");

    mgr.respond(checkpoint.id(),
        approved ? HitlResponse.approve() : HitlResponse.reject("Not now"));
});

try (var agentican = Agentican.builder()
        .config(config)
        .toolkit("notion", notionToolkit)
        .hitlManager(hitlManager)
        .build()) {

    agentican.run("Create a Notion page summarizing yesterday's meeting").result();
}
```

## Status

Agentican is under active development. The core APIs are stabilizing but may change before 1.0. Pin to a specific version in production.

## License

Apache 2.0 — see [LICENSE](LICENSE) for details.

## Contributing

Issues and pull requests welcome. See [CONTRIBUTING.md](CONTRIBUTING.md) for guidelines.
