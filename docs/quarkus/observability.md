# Observability

## Micrometer Metrics

Requires `agentican-quarkus-metrics`. Metrics appear at `/q/metrics` in Prometheus format.

### LLM metrics

Recorded by `MeteredLlmClient` — wraps every config-built LLM client.

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

### Tool metrics

Recorded by `MeteredToolkit` — wraps every toolkit (MCP, Composio, custom, built-in).

| Metric | Type | Tags |
|---|---|---|
| `agentican_tool_calls_total` | counter | `toolkit`, `tool` |
| `agentican_tool_duration_seconds` | timer | `toolkit`, `tool` |
| `agentican_tool_errors_total` | counter | `toolkit`, `tool`, `error` |

### Agent run and turn metrics

Recorded by `MeteredTurnListener` — tracks per-run and per-turn execution.

| Metric | Type | Tags |
|---|---|---|
| `agentican_agent_runs_total` | counter | `agent`, `status` |
| `agentican_agent_turns_total` | counter | `agent`, `step`, `stop_reason` |
| `agentican_agent_turns_tokens_input_total` | counter | `agent` |
| `agentican_agent_turns_tokens_output_total` | counter | `agent` |

### Task lifecycle metrics

Recorded by `AgenticanMetricsObserver` — observes CDI lifecycle events.

| Metric | Type | Tags |
|---|---|---|
| `agentican_tasks_active` | gauge | — |
| `agentican_tasks_completed_total` | counter | `status` |
| `agentican_tasks_duration_seconds` | timer | `status` |
| `agentican_steps_completed_total` | counter | `status` |
| `agentican_hitl_checkpoints_created_total` | counter | `type` |
| `agentican_hitl_checkpoints_pending` | gauge | — |

### Example Prometheus queries

```promql
# Token spend per minute by model
sum(rate(agentican_llm_tokens_output_total[5m])) by (model)

# Task success rate
sum(rate(agentican_tasks_completed_total{status="COMPLETED"}[1h]))
  / sum(rate(agentican_tasks_completed_total[1h]))

# p99 LLM latency
histogram_quantile(0.99,
  sum(rate(agentican_llm_duration_seconds_bucket[5m])) by (le, model))

# Stuck HITL alert
agentican_hitl_checkpoints_pending > 5
```

## OpenTelemetry Tracing

Requires `agentican-quarkus-otel`. Exports spans to any OTLP-compatible collector.

```properties
quarkus.otel.exporter.otlp.endpoint=http://localhost:4317
```

### Span hierarchy

```
[POST /agentican/tasks]                         ← Quarkus auto
  └── [agentican.step research]                 ← TracedStepListener
       └── [agentican.run]                      ← TracedTurnListener
            ├── [agentican.turn 0]              ← TracedTurnListener
            │    ├── [agentican.llm.call]        ← TracedLlmClient
            │    │    gen_ai.system = anthropic
            │    │    gen_ai.request.model = claude-sonnet-4-5
            │    │    gen_ai.usage.input_tokens = 100
            │    │    gen_ai.usage.output_tokens = 50
            │    └── [agentican.tool.call]        ← TracedToolkit
            │         agentican.toolkit.slug = github
            │         agentican.tool.name = search_repos
            └── [agentican.turn 1]
                 └── [agentican.llm.call]
```

### Span attributes

**LLM calls** — [Gen AI semantic conventions](https://opentelemetry.io/docs/specs/semconv/gen-ai/):

| Attribute | Description |
|---|---|
| `gen_ai.system` | LLM provider (e.g. `anthropic`) |
| `gen_ai.request.model` | Model name |
| `gen_ai.usage.input_tokens` | Input tokens |
| `gen_ai.usage.output_tokens` | Output tokens |
| `gen_ai.usage.cache_read_tokens` | Cache read tokens |
| `gen_ai.usage.cache_write_tokens` | Cache write tokens |
| `gen_ai.response.finish_reasons` | Stop reason |

**Steps:** `agentican.task.id`, `agentican.step.name`, `agentican.step.status`

**Runs:** `agentican.agent.name`, `agentican.agent.status`

**Turns:** `agentican.agent.name`, `agentican.turn.index`, `agentican.turn.stop_reason`, `agentican.turn.input_tokens`, `agentican.turn.output_tokens`

**Tool calls:** `agentican.toolkit.slug`, `agentican.tool.name`

**HITL waits:** `agentican.hitl.checkpoint.id`, `agentican.hitl.checkpoint.type`

### Reading traces

`agentican-quarkus-otel` exposes `GET /agentican/traces/{taskId}`, which returns
the list of `SpanView`s for one task. The endpoint is backed by the `SpanStore`
interface:

| Backend | Implementation | Persistence |
|---|---|---|
| Default (in-memory) | `InMemorySpanExporter` | LRU of most recent 100 traces |
| JPA (opt-in) | `JpaSpanExporter` | `spans` table via Flyway `V2__spans.sql` |

`JpaSpanExporter` ships in `agentican-quarkus-otel-store-jpa`; drop the jar on
the classpath and it takes over automatically unless you set
`agentican.store.backend=memory`.

### Context propagation

`TracedTaskDecorator` captures the OTel `Context` from the HTTP thread and restores it
on the task's virtual thread. Step spans become children of the HTTP request span. Turn
spans become children of step spans. LLM and tool call spans nest under turns. All
automatic — no manual span management.

### Loop and branch sub-tasks

OTel context automatically propagates into loop iterations and branch paths. Sub-task step spans are children of the parent loop/branch step span:

```
[agentican.step loop-step]
  └── [agentican.step process]     ← iteration 1 (child of loop-step)
       └── [agentican.run]
            └── [agentican.turn 0]
  └── [agentican.step process]     ← iteration 2 (child of loop-step)
       └── ...
```

This is handled by the `TaskExecutionDecorator` which captures the parent step's OTel context and restores it on the sub-task's thread.

## Dev UI

Requires `agentican-quarkus-deployment`. In dev mode (`./mvnw quarkus:dev`), visit
`/q/dev-ui` and look for the Agentican card:

- **Agents** — registered agents with roles and skills
- **Tasks** — task list with status and token counts (refresh button)
- **Checkpoints** — pending HITL checkpoints
- **Knowledge** — knowledge entries with fact counts
