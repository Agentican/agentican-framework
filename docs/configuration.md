# Configuration

Agentican is configured via `RuntimeConfig`. You can build it programmatically or load from YAML.

## RuntimeConfig

```java
record RuntimeConfig(
    List<LlmConfig> llm,           // at least one required
    List<McpConfig> mcp,
    ComposioConfig composio,
    WorkerConfig agentRunner,
    List<AgentConfig> agents,
    List<PlanConfig> plans
)
```

Build with the fluent API:

```java
var config = RuntimeConfig.builder()
        .llm(LlmConfig.builder().apiKey(apiKey).build())
        .worker(WorkerConfig.builder().maxTurns(20).build())
        .agent(AgentConfig.builder().name("researcher").role("...").build())
        .build();
```

## LlmConfig

```java
LlmConfig.builder()
        .name("default")          // optional, defaults to "default"
        .provider("anthropic")    // currently only "anthropic" supported
        .model("claude-sonnet-4-5")
        .apiKey(apiKey)
        .maxTokens(16384)
        .build()
```

You can register multiple LLMs and assign them to specific agents:

```java
RuntimeConfig.builder()
        .llm(LlmConfig.builder().name("default").apiKey(key).model("claude-sonnet-4-5").build())
        .llm(LlmConfig.builder().name("fast").apiKey(key).model("claude-haiku-4-5").build())
        .agent(AgentConfig.builder().name("classifier").role("...").llm("fast").build())
        .build();
```

## WorkerConfig

Controls the agent runner (`SmacAgentRunner`) defaults:

```java
WorkerConfig.builder()
        .maxTurns(10)                                  // max LLM turns per agent step
        .timeout(Duration.ofMinutes(30))               // per-step timeout
        .maxStepRetries(5)                             // max retries on step failure (default 3)
        .llmMaxRetries(3)                              // max LLM call retry attempts (default 3)
        .llmRetryBaseDelay(Duration.ofSeconds(1))      // base delay, doubles each retry (default 1s)
        .build()
```

### LLM Retry

LLM calls are automatically retried on transient failures (rate limits, server errors, network issues) with exponential backoff and jitter. This is handled by `RetryingLlmClient`, which wraps every LLM client at construction time — all callers (agent runner, planner, fact extractor) get retries automatically.

Configure via `WorkerConfig`:

```java
WorkerConfig.builder()
        .llmMaxRetries(3)                        // max retry attempts (default 3)
        .llmRetryBaseDelay(Duration.ofSeconds(1)) // base delay, doubles each retry (default 1s)
        .build()
```

Retryable errors: `IOException`, `TimeoutException`, HTTP 429/500/503/529, rate limit responses.

Non-retryable errors fail immediately: bad request (400), authentication (401), validation errors.

On exhaustion, throws `LlmCallException` with agent name, step name, turn index, and attempt count.

### LLM Streaming

`LlmClient` supports optional token streaming via `sendStreaming()`:

```java
default LlmResponse sendStreaming(LlmRequest request, Consumer<String> onToken) {
    // Default: falls back to send(), fires callback once with complete text
}
```

LLM client implementations can override this to stream tokens incrementally. The `SmacAgentRunner` calls `sendStreaming()` automatically, passing each token to `TaskListener.onToken()`.

Observe tokens in real time:

```java
public class MyListener implements TaskListener {
    @Override
    public void onToken(String taskId, String turnId, String token) {
        System.out.print(token); // real-time output
    }
}
```

## AgentConfig

```java
AgentConfig.builder()
        .name("researcher")
        .role("Expert at finding and synthesizing information")
        .llm("default")              // optional, uses default LLM if omitted
        .skill(SkillConfig.of("citations", "Always cite sources"))
        .build()
```

Agents from config are pre-registered when Agentican starts. They can be referenced by name in task steps.

## ComposioConfig

```java
ComposioConfig.builder()
        .apiKey(composioApiKey)
        .userId("user@example.com")
        .build()
```

On startup, Agentican calls Composio's API to discover the user's connected toolkits. Each becomes available as a toolkit slug (e.g., `notion`, `github`).

## McpConfig

```java
McpConfig.builder()
        .slug("filesystem")
        .name("Local Filesystem MCP")
        .url("http://localhost:3000")
        .header("Authorization", "Bearer " + token)
        .queryParam("session", "my-session")
        .build()
```

Multiple MCP servers can be registered. Each is accessed via its `slug`.

The framework tries Streamable HTTP transport first, then falls back to SSE.

## YAML Configuration

For applications that need external configuration, load from YAML:

```yaml
llm:
  - apiKey: ${ANTHROPIC_API_KEY}
    model: claude-sonnet-4-5

agentRunner:
  maxTurns: 15
  timeout: PT30M

composio:
  apiKey: ${COMPOSIO_API_KEY}
  userId: user@example.com

agents:
  - name: researcher
    role: Expert researcher who finds and synthesizes information

  - name: writer
    role: Documentation specialist with clear, concise writing style
```

```java
var config = RuntimeConfig.load(Path.of("agentican.yml"));
```

Environment variables in `${VAR}` form are resolved at load time.

## Agentican Builder

The `Agentican.builder()` accepts the runtime config plus optional overrides:

```java
Agentican.builder()
        .config(config)                            // required
        .llm("name", llmClient)                    // pre-built LLM client (overrides config)
        .toolkit("slug", toolkit)                  // custom toolkit
        .hitlManager(hitlManager)                  // HITL coordinator (default: logging notifier)
        .taskStateStore(taskStateStore)                // log persistence (default: in-memory)
        .knowledgeStore(knowledgeStore)            // custom knowledge store (default: in-memory)
        .llmDecorator(llmDecorator)                // decorates every LLM client (used by metrics/OTel)
        .taskDecorator(taskDecorator)              // decorates task submission (used by OTel for context propagation)
        .stepListener(stepListener)                // listens to task/step/run/turn lifecycle events
        .taskExecutor(executor)                    // custom executor for task virtual threads
        .build();
```

### Extension Points

| Interface | Purpose |
|---|---|
| `TaskListener` | Receives lifecycle events for tasks, steps, runs, turns, messages, responses, tool calls, and HITL. Used by OTel for span creation and metrics for counters. |
| `LlmClientDecorator` | Wraps every config-built `LlmClient`. Used by metrics (counters/timers) and OTel (LLM call spans). |
| `TaskDecorator` | Wraps the `Supplier` passed to `CompletableFuture.supplyAsync()` for each task. Used by OTel to propagate trace context to virtual threads. Supports `snapshot()` for step-level context capture. |

### Pre-built LLM Clients

If you want to inject your own `LlmClient` (for testing, custom providers, or to wrap with logging/retry/caching), use `.llm(name, llmClient)`. This takes precedence over `LlmConfig` entries with the same name.

```java
LlmClient cachedClient = LlmClient.withLogging(myCustomClient);

Agentican.builder()
        .config(config)
        .llm("default", cachedClient)
        .build();
```

### Custom HitlManager

If not provided, Agentican creates one with a logging notifier (auto-approves, logs each checkpoint).

```java
Agentican.builder()
        .config(config)
        .hitlManager(new HitlManager(myNotifier, Duration.ofHours(2)))
        .build();
```

### Custom TaskStateStore

If not provided, Agentican creates a `MemTaskStateStore`. Implement your own for durable storage:

The `TaskStateStore` interface uses granular mutation methods for each lifecycle event (e.g., `taskStarted()`, `stepStarted()`, `runStarted()`, `turnStarted()`, `messageSent()`, `responseReceived()`, `toolCallStarted()`, etc.) plus query methods:

```java
TaskLog load(String taskId);
List<TaskLog> list();
```

The default `MemTaskStateStore` stores everything in memory. For durable storage, implement the full `TaskStateStore` interface against your database.

```java
Agentican.builder()
        .config(config)
        .taskStateStore(new DatabaseTaskStateStore(dataSource))
        .build();
```

## Environment Variables

The default `AnthropicLlmClient` uses the `apiKey` from `LlmConfig`. The convention is to source it from an environment variable:

```java
LlmConfig.builder()
        .apiKey(System.getenv("ANTHROPIC_API_KEY"))
        .build()
```

In YAML, use `${VAR}` substitution:

```yaml
llm:
  - apiKey: ${ANTHROPIC_API_KEY}
```

## Lifecycle

`Agentican` implements `AutoCloseable`. Use try-with-resources:

```java
try (var agentican = Agentican.builder().config(config).build()) {
    // ...
} // automatically closes the virtual thread executor and toolkits
```

`close()` shuts down the task executor (waiting for in-flight tasks to finish) and closes any toolkits that implement `AutoCloseable` (e.g., MCP connections).
