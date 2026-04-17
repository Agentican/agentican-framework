# Agentican Quarkus Deployment

> Build-time processor for the Agentican Quarkus extension.

This module runs during `quarkus:build`, not at application runtime. It registers the framework's beans with Arc, wires in reflection hints for GraalVM native image, and contributes the Agentican pages to the Quarkus Dev UI.

## Should I depend on this?

Usually, no. `agentican-quarkus-runtime` declares this as its build-time companion, so if you already depend on the runtime module you're covered. List it directly only if you're building a custom Quarkus extension that layers on top of Agentican.

```xml
<dependency>
    <groupId>ai.agentican</groupId>
    <artifactId>agentican-quarkus-deployment</artifactId>
    <version>0.1.0-alpha.1</version>
    <!-- build-time only; typically not declared explicitly -->
</dependency>
```

## What's inside

- **`AgenticanProcessor`** — registers the `agentican` feature, declares framework beans as unremovable, and registers reflection hints for 30+ framework classes (configs, plans, HITL, knowledge, LLM models) so native builds work.
- **`AgenticanDevUIProcessor`** — wires the agents / tasks / checkpoints / knowledge panels into `/q/dev-ui`.
- **`AgenticanDevServicesProcessor`** — Dev Services glue (mostly for tests).

## Related

- [`quarkus-runtime`](../quarkus-runtime/) — the runtime-facing module you actually `@Inject` from.
- [Top-level module index](../README.md#modules).
