# Agentican Quarkus Integration Tests

> Cross-module smoke tests for the Quarkus extension.

Not a deployable artifact (`maven.deploy.skip=true`). This module pulls every major Quarkus module — `runtime`, `rest`, `metrics`, `otel`, `store-jpa`, `otel-store-jpa`, `scheduler`, `test` — onto a single classpath and exercises them together. It's the canary that catches breaks in the spaces between modules.

## What it covers

- **Bean discovery across modules** — every module's CDI beans register and wire together.
- **Jandex indexing** — `quarkus.index-dependency.*` is set correctly so annotated classes in dependency jars are discovered.
- **Decorator / listener stacking** — `LlmClientDecorator`s and `WorkflowRunDecorator`s from multiple modules compose in the right order.
- **End-to-end task lifecycle** — submit via REST, stream events via SSE, assert persistence, metrics, and traces.

`MockLlmClient` (from `quarkus-test`) is injected as the LLM so tests are deterministic.

## Running

```bash
./mvnw test -pl quarkus-integration-tests
```

Runs in-process with `@QuarkusTest`. No external services required — JPA uses an in-memory database for the test profile.

## Where to find it

- [`MultiModuleCompositionTest`](src/test/java/ai/agentican/quarkus/it/MultiModuleCompositionTest.java) — one `@QuarkusTest` class with a handful of focused `@Test` methods.

## Related

- [Top-level module index](../README.md#modules).
