# Agentican Quarkus Metrics

Micrometer metrics for Agentican on Quarkus. Opt-in: add this dependency and all LLM
calls, task lifecycle events, and HITL checkpoints are automatically instrumented.
Metrics appear at `/q/metrics` in Prometheus format.

## Setup

```xml
<dependency>
    <groupId>ai.agentican</groupId>
    <artifactId>agentican-quarkus-metrics</artifactId>
    <version>0.1.0-alpha.1</version>
</dependency>
```

That's it. No configuration needed — the module auto-registers:
- A `LlmClientDecorator` that wraps every config-built LLM client with Micrometer timers/counters
- An event observer that records task/step/HITL lifecycle metrics from CDI events

## Metric reference

### LLM metrics

Recorded by `MeteredLlmClient` which wraps every LLM client built from config.

| Metric | Type | Tags | Description |
|---|---|---|---|
| `agentican_llm_requests_total` | counter | `llm`, `model`, `stop_reason` | Total LLM API calls |
| `agentican_llm_duration_seconds` | timer | `llm`, `model` | Per-call wall-clock time |
| `agentican_llm_tokens_input_total` | counter | `llm`, `model` | Input tokens consumed |
| `agentican_llm_tokens_output_total` | counter | `llm`, `model` | Output tokens consumed |
| `agentican_llm_tokens_cache_read_total` | counter | `llm`, `model` | Cache read tokens |
| `agentican_llm_tokens_cache_write_total` | counter | `llm`, `model` | Cache write tokens |
| `agentican_llm_web_searches_total` | counter | `llm`, `model` | Web search requests |
| `agentican_llm_errors_total` | counter | `llm`, `model`, `error` | LLM call failures |

**Tags:**
- `llm` — the LLM client name from config (e.g. `"default"`, `"fast"`)
- `model` — the model string (e.g. `"claude-sonnet-4-5"`)
- `stop_reason` — `END_TURN`, `TOOL_USE`, `MAX_TOKENS`
- `error` — exception class name (e.g. `RuntimeException`, `TimeoutException`)

### Task lifecycle metrics

Recorded by `AgenticanMetricsObserver` from CDI lifecycle events.

| Metric | Type | Tags | Description |
|---|---|---|---|
| `agentican_tasks_active` | gauge | — | Currently in-flight tasks |
| `agentican_tasks_completed_total` | counter | `status` | Tasks that reached terminal state |
| `agentican_tasks_duration_seconds` | timer | `status` | Task wall-clock time |
| `agentican_steps_completed_total` | counter | `status` | Steps that reached terminal state |
| `agentican_hitl_checkpoints_created_total` | counter | `type` | HITL checkpoints created |
| `agentican_hitl_checkpoints_pending` | gauge | — | Currently pending HITL checkpoints |

### Tool metrics

Recorded by `MeteredToolkit` which wraps every toolkit via the `ToolkitDecorator`.

| Metric | Type | Tags | Description |
|---|---|---|---|
| `agentican_tool_calls_total` | counter | `toolkit`, `tool` | Total tool invocations |
| `agentican_tool_duration_seconds` | timer | `toolkit`, `tool` | Per-call wall-clock time |
| `agentican_tool_errors_total` | counter | `toolkit`, `tool`, `error` | Tool call failures |

### Run and turn metrics

Recorded by `AgenticanMetricsObserver` from step listener events.

| Metric | Type | Tags | Description |
|---|---|---|---|
| `agentican_agent_runs_total` | counter | `agent`, `step` | Total agent runs |
| `agentican_agent_turns_total` | counter | `agent`, `step`, `stop_reason` | Total LLM turns |
| `agentican_agent_turns_tokens_input_total` | counter | `agent` | Input tokens per turn |
| `agentican_agent_turns_tokens_output_total` | counter | `agent` | Output tokens per turn |

**Tags:**
- `status` — `COMPLETED`, `FAILED`, `CANCELLED`
- `type` — `TOOL_CALL`, `STEP_OUTPUT`, `QUESTION`

## Prometheus query examples

```promql
# Cost: total output tokens in the last hour
sum(increase(agentican_llm_tokens_output_total[1h]))

# Cost: tokens per minute by model
sum(rate(agentican_llm_tokens_output_total[5m])) by (model)

# Performance: p99 LLM call latency
histogram_quantile(0.99,
  sum(rate(agentican_llm_duration_seconds_bucket[5m])) by (le, model))

# Reliability: task success rate over 1 hour
sum(rate(agentican_tasks_completed_total{status="COMPLETED"}[1h]))
  / sum(rate(agentican_tasks_completed_total[1h]))

# Reliability: LLM error rate
sum(rate(agentican_llm_errors_total[5m])) by (model, error)

# HITL: alert on stuck checkpoints
agentican_hitl_checkpoints_pending > 5

# System: active task count
agentican_tasks_active
```

## How it works

### LLM client decoration

The module produces a `LlmClientDecorator` bean. The `AgenticanProducer` in `agentican-quarkus`
detects it and passes it to the `AgenticanRuntime.builder()`. During construction, every LLM client
built from `agentican.llm[*]` config passes through the decorator and gets wrapped with
`MeteredLlmClient`.

**Note:** Pre-built `@Named` LLM client beans (e.g. test mocks) bypass the decorator because
they're registered after config-built clients. This is intentional — test mocks shouldn't
incur metric overhead.

### Lifecycle event observation

`AgenticanMetricsObserver` uses `@Observes` on the CDI events fired by `agentican-quarkus`'s
event bridge (`EventFiringTaskLogStore`). No framework changes needed — it's pure CDI
observation.

The observer is annotated with `@Startup` so gauges (`tasks_active`, `checkpoints_pending`)
are registered at application start, not lazily on first event.

## What's not included

- **OpenTelemetry tracing** — shipped in the sibling `agentican-quarkus-otel`
  module. Add it alongside metrics for full LLM-call and step-level span
  instrumentation with Gen AI semantic attributes.
- **Per-agent metrics** — LLM metrics are tagged by client name, not by agent. Agent-level
  detail comes from step lifecycle metrics. Cross-referencing agent → step is an analytics
  concern best handled in dashboards.
- **Cost estimation** — converting tokens to dollars requires model pricing data. Compute
  this in your dashboard from the raw token counters.
