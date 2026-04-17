# Getting Started

## Prerequisites

- Java 25+
- Quarkus 3.31+
- An Anthropic API key

## Add the dependency

All modules are on [Maven Central](https://central.sonatype.com/namespace/ai.agentican). No custom repository needed — Quarkus resolves each module's build-time companion automatically.

For CDI-only integration:

```xml
<dependency>
    <groupId>ai.agentican</groupId>
    <artifactId>agentican-quarkus-runtime</artifactId>
    <version>0.1.0-alpha.1</version>
</dependency>
```

For the full stack (REST + metrics + tracing + Dev UI):

```xml
<dependency>
    <groupId>ai.agentican</groupId>
    <artifactId>agentican-quarkus-runtime</artifactId>
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

For Postgres persistence (task state, knowledge, registries, spans):

```xml
<dependency>
    <groupId>ai.agentican</groupId>
    <artifactId>agentican-quarkus-store-jpa</artifactId>
    <version>0.1.0-alpha.1</version>
</dependency>
<dependency>
    <groupId>ai.agentican</groupId>
    <artifactId>agentican-quarkus-otel-store-jpa</artifactId>
    <version>0.1.0-alpha.1</version>
</dependency>
<dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-jdbc-postgresql</artifactId>
</dependency>
```

## Configure

Add to `application.properties`:

```properties
# Required: at least one LLM
agentican.llm[0].api-key=${ANTHROPIC_API_KEY}
agentican.llm[0].model=claude-sonnet-4-5

# Optional: pre-register agents (external-id is required when declared in config)
agentican.agents[0].name=researcher
agentican.agents[0].role=Expert at finding and synthesizing information
agentican.agents[0].external-id=researcher

# Optional: tune the agent runner
agentican.agent-runner.max-turns=15
agentican.agent-runner.timeout=PT10M
agentican.agent-runner.task-timeout=PT1H
```

### Persistence (optional)

With `agentican-quarkus-store-jpa` on the classpath, task state, knowledge,
agents, skills, and plans persist to Postgres. Dev Services handles the
container lifecycle:

```properties
quarkus.datasource.db-kind=postgresql
quarkus.datasource.devservices.enabled=true
quarkus.datasource.devservices.reuse=true

quarkus.hibernate-orm.database.generation=none
quarkus.flyway.migrate-at-start=true
quarkus.flyway.locations=classpath:db/migration

quarkus.index-dependency.agentican-store-jpa.group-id=ai.agentican
quarkus.index-dependency.agentican-store-jpa.artifact-id=agentican-quarkus-store-jpa
quarkus.index-dependency.agentican-otel-store-jpa.group-id=ai.agentican
quarkus.index-dependency.agentican-otel-store-jpa.artifact-id=agentican-quarkus-otel-store-jpa
```

For data to survive `quarkus:dev` restarts, enable Testcontainers reuse once in
`~/.testcontainers.properties`:

```properties
testcontainers.reuse.enable=true
```

Without this, the container (and all data) is destroyed when the JVM exits,
even with `devservices.reuse=true`.

## Run your first task

### Imperative style

```java
@ApplicationScoped
public class MyService {

    @Inject Agentican agentican;

    public String research(String topic) {
        var handle = agentican.run("Research " + topic + " and summarize findings");
        var result = handle.result(); // blocks until complete
        return result.lastOutput();
    }
}
```

### Reactive style

```java
@ApplicationScoped
public class MyService {

    @Inject ReactiveAgentican agentican;

    public Uni<String> research(String topic) {
        return agentican.runAndAwait("Research " + topic)
            .onItem().transform(TaskResult::lastOutput);
    }
}
```

### Via REST API

With `agentican-quarkus-rest` on the classpath, endpoints are auto-discovered:

```bash
# Submit
curl -X POST http://localhost:8080/agentican/tasks \
  -H "content-type: application/json" \
  -d '{"description": "Research agent frameworks"}'

# Poll status
curl http://localhost:8080/agentican/tasks/{taskId}

# Get full log
curl http://localhost:8080/agentican/tasks/{taskId}/log
```

## Verify it works

Start Quarkus in dev mode:

```bash
./mvnw quarkus:dev
```

Check the health endpoint:

```bash
curl http://localhost:8080/q/health
```

You should see `agentican` in the liveness check and `agentican-readiness` in the readiness check, both UP.

If `agentican-quarkus-deployment` is on the classpath, visit the Dev UI at
`http://localhost:8080/q/dev-ui` and look for the Agentican card.

## Plan reuse across restarts

With `agentican-quarkus-store-jpa` on the classpath, `PlannerAgent` reuses
persisted plans. The first run materializes and stores the plan under its
`external_id`; subsequent runs with a matching `external_id` load the prior
definition instead of re-planning. Without the JPA store, plans are held in
memory and disappear on restart.

## Next steps

- [CDI Integration](cdi.md) — qualifiers, events, bean overrides, reactive support
- [REST & Real-Time](rest.md) — full endpoint reference, SSE, WebSocket
- [Configuration Reference](configuration.md) — all properties
