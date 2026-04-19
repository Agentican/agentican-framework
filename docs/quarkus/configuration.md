# Agentican Quarkus Configuration Reference

Complete list of all `agentican.*` configuration properties across all Quarkus modules.

## Core (`agentican-quarkus`)

### LLM Clients

| Property | Type | Default | Description |
|---|---|---|---|
| `agentican.llm[*].name` | string | `default` | Client name (referenced by agents) |
| `agentican.llm[*].provider` | string | `anthropic` | LLM provider |
| `agentican.llm[*].model` | string | — | Model name (e.g. `claude-sonnet-4-5`) |
| `agentican.llm[*].api-key` | string | **required** | API key |
| `agentican.llm[*].max-tokens` | int | `16384` | Max output tokens per call |

### Agent Runner

| Property | Type | Default | Description |
|---|---|---|---|
| `agentican.agent-runner.max-turns` | int | `10` | Max LLM turns per agent step |
| `agentican.agent-runner.timeout` | Duration | `PT30M` | Per-step timeout |
| `agentican.agent-runner.task-timeout` | Duration | — | Per-task overall timeout (omit for no limit) |

### Recovery

| Property | Type | Default | Description |
|---|---|---|---|
| `agentican.resume-on-start` | boolean | `true` | When `true`, `AgenticanService.resumeInterrupted` runs on `StartupEvent` to pick up tasks left in-flight after a restart |
| `agentican.resume-max-concurrent` | int | `10` | Max concurrent task resumes during startup recovery |

### Composio Integration

| Property | Type | Default | Description |
|---|---|---|---|
| `agentican.composio.api-key` | string | — | Composio API key (omit to disable) |
| `agentican.composio.user-id` | string | — | Composio user ID |

### MCP Servers

| Property | Type | Default | Description |
|---|---|---|---|
| `agentican.mcp[*].slug` | string | **required** | Toolkit slug identifier |
| `agentican.mcp[*].name` | string | **required** | Human-readable name |
| `agentican.mcp[*].url` | string | **required** | Server URL |
| `agentican.mcp[*].query-params.<key>` | string | — | Query parameters |
| `agentican.mcp[*].headers.<key>` | string | — | HTTP headers |

### Pre-registered Agents

| Property | Type | Default | Description |
|---|---|---|---|
| `agentican.agents[*].id` | string | auto | Internal primary key (omit; generated) |
| `agentican.agents[*].external-id` | string | **required** | Stable business key for persistence |
| `agentican.agents[*].name` | string | **required** | Agent name |
| `agentican.agents[*].role` | string | **required** | Agent role description |
| `agentican.agents[*].llm` | string | `default` | LLM client name to use |

### Pre-registered Skills

| Property | Type | Default | Description |
|---|---|---|---|
| `agentican.skills[*].id` | string | auto | Internal primary key (omit; generated) |
| `agentican.skills[*].external-id` | string | **required** | Stable business key for persistence |
| `agentican.skills[*].name` | string | **required** | Skill name |
| `agentican.skills[*].instructions` | string | **required** | Skill instructions |

> `external-id` is enforced at boot. Any config-declared agent or skill without
> one causes Agentican to throw `IllegalStateException` during
> `Agentican.builder().build()`. Planner-created entities don't carry one —
> they exist only within the lifetime of the process / DB row.

### Store backend selection

| Property | Type | Default | Description |
|---|---|---|---|
| `agentican.store.backend` | string | `jpa` (when the module is present), otherwise `memory` | Set to `memory` to force in-memory stores even if `agentican-quarkus-store-jpa` is on the classpath |

## Scheduler (`agentican-quarkus-scheduler`)

| Property | Type | Default | Description |
|---|---|---|---|
| `agentican.scheduled[*].name` | string | **required** | Scheduled task name |
| `agentican.scheduled[*].cron` | string | **required** | Cron expression (Quartz format) |
| `agentican.scheduled[*].description` | string | **required** | Task description (sent to planner) |
| `agentican.scheduled[*].enabled` | boolean | `true` | Enable/disable the scheduled task |

## Cross-module Indexing

When using `agentican-quarkus` as a dependency (without `agentican-quarkus-deployment`),
add these properties so Quarkus discovers the CDI beans:

```properties
quarkus.index-dependency.agentican-quarkus.group-id=ai.agentican
quarkus.index-dependency.agentican-quarkus.artifact-id=agentican-quarkus-runtime
```

**Note:** If you use `agentican-quarkus-deployment`, this is handled automatically by the
deployment processor — no manual config needed.

For additional modules, add similar entries:

```properties
# REST module
quarkus.index-dependency.agentican-rest.group-id=ai.agentican
quarkus.index-dependency.agentican-rest.artifact-id=agentican-quarkus-rest

# Metrics module
quarkus.index-dependency.agentican-metrics.group-id=ai.agentican
quarkus.index-dependency.agentican-metrics.artifact-id=agentican-quarkus-metrics

# OTel module
quarkus.index-dependency.agentican-otel.group-id=ai.agentican
quarkus.index-dependency.agentican-otel.artifact-id=agentican-quarkus-otel

# Test utilities
quarkus.index-dependency.agentican-test.group-id=ai.agentican
quarkus.index-dependency.agentican-test.artifact-id=agentican-quarkus-test

# JPA store
quarkus.index-dependency.agentican-store-jpa.group-id=ai.agentican
quarkus.index-dependency.agentican-store-jpa.artifact-id=agentican-quarkus-store-jpa

# JPA OTel store
quarkus.index-dependency.agentican-otel-store-jpa.group-id=ai.agentican
quarkus.index-dependency.agentican-otel-store-jpa.artifact-id=agentican-quarkus-otel-store-jpa
```

## CDI Bean Overrides

The following beans are produced with `@DefaultBean` — override by producing your own:

| Bean | Default | Override by producing |
|---|---|---|
| `HitlManager` | Logging notifier | `@Produces HitlManager` |
| `KnowledgeStore` | `MemKnowledgeStore` | `@Produces KnowledgeStore` |
| `TaskStateStore` | `MemTaskStateStore` | `@Produces TaskStateStore` |
| `AgentRegistry` | `InMemoryAgentRegistry` | `@Produces AgentRegistry` |
| `SkillRegistry` | `InMemorySkillRegistry` | `@Produces SkillRegistry` |
| `PlanRegistry` | `InMemoryPlanRegistry` | `@Produces PlanRegistry` |

With `agentican-quarkus-store-jpa` on the classpath, JPA-backed implementations
of all six registries/stores auto-activate and supersede these defaults.

## Persistence (`agentican-quarkus-store-jpa`, `agentican-quarkus-otel-store-jpa`)

The JPA stores use the standard Quarkus datasource + Flyway properties:

```properties
quarkus.datasource.db-kind=postgresql
quarkus.datasource.devservices.enabled=true
quarkus.datasource.devservices.reuse=true

quarkus.hibernate-orm.database.generation=none
quarkus.flyway.migrate-at-start=true
quarkus.flyway.locations=classpath:db/migration
```

`V1__init.sql` (from `store-jpa`) creates catalog, plan, task-execution, and
knowledge tables. `V2__spans.sql` (from `otel-store-jpa`) adds the `spans`
table.

For data to survive `quarkus:dev` restarts, enable Testcontainers reuse in
`~/.testcontainers.properties`:

```properties
testcontainers.reuse.enable=true
```

## Metrics (`agentican-quarkus-metrics`)

No Agentican-specific properties. Metrics are exposed at `/q/metrics` automatically.
Configure the Prometheus registry via standard Quarkus properties:

```properties
# Already enabled by default when quarkus-micrometer-registry-prometheus is present
quarkus.micrometer.enabled=true
```

### Metrics produced

| Metric | Type | Tags |
|---|---|---|
| `agentican_llm_requests_total` | counter | `llm`, `model`, `stop_reason` |
| `agentican_llm_duration_seconds` | timer | `llm`, `model` |
| `agentican_llm_tokens_input_total` | counter | `llm`, `model` |
| `agentican_llm_tokens_output_total` | counter | `llm`, `model` |
| `agentican_llm_tokens_cache_read_total` | counter | `llm`, `model` |
| `agentican_llm_tokens_cache_write_total` | counter | `llm`, `model` |
| `agentican_llm_web_searches_total` | counter | `llm`, `model` |
| `agentican_llm_errors_total` | counter | `llm`, `model`, `error` |
| `agentican_tool_calls_total` | counter | `toolkit`, `tool` |
| `agentican_tool_duration_seconds` | timer | `toolkit`, `tool` |
| `agentican_tool_errors_total` | counter | `toolkit`, `tool`, `error` |
| `agentican_tasks_active` | gauge | — |
| `agentican_tasks_completed_total` | counter | `status` |
| `agentican_tasks_duration_seconds` | timer | `status` |
| `agentican_steps_completed_total` | counter | `status` |
| `agentican_hitl_checkpoints_created_total` | counter | `type` |
| `agentican_hitl_checkpoints_pending` | gauge | — |
| `agentican_agent_turns_total` | counter | `agent`, `step`, `stop_reason` |
| `agentican_agent_turns_tokens_input_total` | counter | `agent` |
| `agentican_agent_turns_tokens_output_total` | counter | `agent` |

## OpenTelemetry (`agentican-quarkus-otel`)

No Agentican-specific properties. Configure the OTel exporter via standard Quarkus properties:

```properties
quarkus.otel.exporter.otlp.endpoint=http://localhost:4317
quarkus.otel.exporter.otlp.protocol=grpc
```

### Spans produced

| Span | Attributes |
|---|---|
| `agentican.step {name}` | `agentican.task.id`, `agentican.step.name`, `agentican.step.status` |
| `agentican.turn {N}` | `agentican.agent.name`, `agentican.turn.index`, `agentican.turn.stop_reason`, `agentican.turn.input_tokens`, `agentican.turn.output_tokens` |
| `agentican.llm.call` | `gen_ai.system`, `gen_ai.request.model`, `gen_ai.usage.*`, `gen_ai.response.finish_reasons` |
| `agentican.tool.call` | `agentican.toolkit.slug`, `agentican.tool.name` |
| `agentican.hitl.wait` | `agentican.hitl.checkpoint.id`, `agentican.hitl.checkpoint.type` |

## Reactive Support

`ReactiveAgentican` wraps `Agentican` and returns Mutiny `Uni` types instead of blocking.
Inject it in reactive REST endpoints or Vert.x handlers:

```java
@Inject
ReactiveAgentican agentican;

@POST
@Path("/tasks")
public Uni<TaskId> submit(TaskRequest request) {
    return agentican.submitTask(request.description());
}

@GET
@Path("/tasks/{id}/log")
public Uni<TaskLog> taskLog(@PathParam("id") String taskId) {
    return agentican.getTaskLog(taskId);
}
```

`ReactiveAgentican` is produced automatically by `agentican-quarkus`. It offloads blocking
operations to the framework's virtual thread executor and returns results on the Vert.x
event loop. Use it when your endpoints run on the event loop (non-`@Blocking` RESTEasy
Reactive endpoints).

## REST (`agentican-quarkus-rest`)

No Agentican-specific properties. Endpoints are mounted at `/agentican/*`.
Configure via standard Quarkus HTTP properties:

```properties
# Add a global prefix (results in /api/v1/agentican/*)
quarkus.http.root-path=/api/v1

# CORS for browser clients
quarkus.http.cors.enabled=true
quarkus.http.cors.origins=https://your-ui.example.com
```

### Endpoints

| Method | Path | Description |
|---|---|---|
| POST | `/agentican/tasks` | Submit task (planner or pre-built) |
| GET | `/agentican/tasks` | List tasks (`?limit=`, `?status=`, `?since=`) |
| GET | `/agentican/tasks/{id}` | Task summary |
| GET | `/agentican/tasks/{id}/log` | Full task log |
| GET | `/agentican/tasks/{id}/stream` | SSE event stream |
| DELETE | `/agentican/tasks/{id}` | Cancel task |
| GET | `/agentican/agents` | List agents |
| GET | `/agentican/agents/{name}` | Agent detail |
| GET | `/agentican/checkpoints` | All pending checkpoints |
| GET | `/agentican/checkpoints/{taskId}` | Checkpoints for task |
| POST | `/agentican/checkpoints/{id}/respond` | Respond to checkpoint |
| POST | `/agentican/checkpoints/{id}/cancel` | Cancel checkpoint |
| POST | `/agentican/knowledge` | Create knowledge entry |
| GET | `/agentican/knowledge` | List entries |
| GET | `/agentican/knowledge/{id}` | Entry with facts |
| DELETE | `/agentican/knowledge/{id}` | Delete entry |
| POST | `/agentican/knowledge/{id}/facts` | Add fact |
| WS | `/agentican/ws` | WebSocket (submit/subscribe/respond/cancel) |
