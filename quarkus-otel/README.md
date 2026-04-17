# Agentican Quarkus OpenTelemetry

Distributed tracing for Agentican on Quarkus. Adds OTel spans for LLM calls, task steps,
and HITL checkpoint wait times with Gen AI semantic attributes. Opt-in: add this dependency
and traces flow automatically.

## Setup

```xml
<dependency>
    <groupId>ai.agentican</groupId>
    <artifactId>agentican-quarkus-otel</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```

Configure the OTel exporter in `application.properties`:

```properties
# Export to an OTLP-compatible collector (Jaeger, Grafana Tempo, etc.)
quarkus.otel.exporter.otlp.endpoint=http://localhost:4317
quarkus.otel.exporter.otlp.protocol=grpc

# Or use HTTP/protobuf
# quarkus.otel.exporter.otlp.protocol=http/protobuf
# quarkus.otel.exporter.otlp.endpoint=http://localhost:4318
```

That's it. The module auto-registers:
- A `LlmClientDecorator` that wraps every LLM call with an OTel span
- A `TaskDecorator` that propagates OTel context to task virtual threads
- A `TaskListener` that creates per-step / per-run / per-turn spans
- An HITL checkpoint observer that tracks human wait time as spans
- An `InMemorySpanExporter` implementing `SpanStore` so exported spans are
  queryable by `taskId` / `traceId`
- A `TracesResource` exposing `GET /agentican/traces/{taskId}` for debugging

### Persistent span storage

By default spans live in an in-memory LRU of 100 traces. For Postgres-backed
persistence, add `agentican-quarkus-otel-store-jpa` — its `JpaSpanExporter`
implements the same `SpanStore` interface and is wired in automatically. See
[otel-store-jpa/README.md](../otel-store-jpa/README.md).

## Span hierarchy

```
task → step → run → turn → llm call / tool call
```

```
[POST /agentican/tasks]                              <- auto by Quarkus
  |
  +-- [agentican.step research]                      <- TracedStepListener
  |     |
  |     +-- [agentican.run 0]                        <- TracedStepListener
  |     |     |
  |     |     +-- [agentican.turn 0]
  |     |     |     +-- [agentican.llm.call]         <- TracedLlmClient
  |     |     |     |     gen_ai.system = anthropic
  |     |     |     |     gen_ai.request.model = claude-sonnet-4-5
  |     |     |     |     gen_ai.usage.input_tokens = 100
  |     |     |     |     gen_ai.usage.output_tokens = 50
  |     |     |     |     gen_ai.response.finish_reasons = TOOL_USE
  |     |     |     |
  |     |     |     +-- [agentican.tool.call]        <- TracedToolkit
  |     |     |           agentican.toolkit.slug = mcp-filesystem
  |     |     |           agentican.tool.name = read_file
  |     |     |
  |     |     +-- [agentican.turn 1]
  |     |           +-- [agentican.llm.call]         <- TracedLlmClient
  |     |                 gen_ai.usage.input_tokens = 150
  |     |                 gen_ai.usage.output_tokens = 80
  |     |                 gen_ai.response.finish_reasons = END_TURN
  |
  +-- [agentican.step synthesize]
  |     +-- [agentican.run 0]
  |           +-- [agentican.turn 0]
  |                 +-- [agentican.llm.call]
  |
  +-- [agentican.hitl.wait]                          <- AgenticanTracingObserver
        agentican.hitl.checkpoint.type = STEP_OUTPUT
        (duration = human response time)
```

## Span attributes

### LLM call spans (`agentican.llm.call`)

Following [OTel Gen AI semantic conventions](https://opentelemetry.io/docs/specs/semconv/gen-ai/):

| Attribute | Type | Description |
|---|---|---|
| `gen_ai.system` | string | LLM provider (e.g. `anthropic`) |
| `gen_ai.request.model` | string | Model name (e.g. `claude-sonnet-4-5`) |
| `gen_ai.usage.input_tokens` | long | Input tokens consumed |
| `gen_ai.usage.output_tokens` | long | Output tokens generated |
| `gen_ai.usage.cache_read_tokens` | long | Cache read tokens |
| `gen_ai.usage.cache_write_tokens` | long | Cache write tokens |
| `gen_ai.response.finish_reasons` | string | Stop reason (`END_TURN`, `TOOL_USE`, `MAX_TOKENS`) |
| `agentican.llm.name` | string | Client name from config (e.g. `default`, `fast`) |

### Run spans (`agentican.run {index}`)

| Attribute | Type | Description |
|---|---|---|
| `agentican.task.id` | string | Parent task ID |
| `agentican.step.name` | string | Parent step name |
| `agentican.run.index` | int | Run index (0-based, increments on retry) |

### Tool call spans (`agentican.tool.call`)

| Attribute | Type | Description |
|---|---|---|
| `agentican.toolkit.slug` | string | Toolkit identifier (e.g. `mcp-filesystem`, `composio-github`) |
| `agentican.tool.name` | string | Tool name within the toolkit |

### Step spans (`agentican.step {name}`)

| Attribute | Type | Description |
|---|---|---|
| `agentican.task.id` | string | Parent task ID |
| `agentican.step.name` | string | Step name |
| `agentican.step.status` | string | Final status (`COMPLETED`, `FAILED`, `SUSPENDED`) |

### HITL wait spans (`agentican.hitl.wait`)

| Attribute | Type | Description |
|---|---|---|
| `agentican.hitl.checkpoint.id` | string | Checkpoint ID |
| `agentican.hitl.checkpoint.type` | string | `TOOL_CALL`, `STEP_OUTPUT`, `QUESTION` |
| `agentican.step.name` | string | Step that created the checkpoint |

The span duration represents the human response time — how long the task was parked
waiting for approval.

## How it works

### Context propagation

Agentican runs tasks on virtual threads. OTel context is thread-local and doesn't
automatically propagate across `CompletableFuture.supplyAsync()`. The `TracedTaskDecorator`
captures `Context.current()` on the caller's thread and restores it on the virtual thread:

```
HTTP thread (has OTel context)
  -> taskDecorator.decorate(supplier)   // captures Context.current()
    -> CompletableFuture.supplyAsync()
      -> virtual thread
        -> Context.makeCurrent()         // restores captured context
          -> step spans become children of the HTTP span
            -> LLM spans become children of the step span
```

### LLM client decoration

Same pattern as `agentican-quarkus-metrics`. The `TracingAutoConfiguration` produces a
`LlmClientDecorator` that wraps each config-built LLM client with `TracedLlmClient`.

### Step listener

The framework's `StepListener` interface (added in this sprint) is called synchronously
on the step's virtual thread. `TracedStepListener` creates a span on `onStepStarted` and
ends it on `onStepCompleted`. Because the span is made current via `Span.makeCurrent()`,
any LLM calls within the step become children of the step span.

## Viewing traces

### Jaeger

```bash
docker run -d --name jaeger \
  -p 16686:16686 \
  -p 4317:4317 \
  jaegertracing/all-in-one:latest
```

```properties
quarkus.otel.exporter.otlp.endpoint=http://localhost:4317
```

Open http://localhost:16686 to see traces.

### Grafana Tempo

```properties
quarkus.otel.exporter.otlp.endpoint=http://tempo:4317
```

## What's not included

- **Planner-specific spans** — the 3-pass planner runs inside the task span.
- **Baggage propagation** — user ID / session ID through traces. Application-specific.
