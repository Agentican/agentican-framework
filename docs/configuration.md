# Configuration

Agentican is configured via `RuntimeConfig`. You can build it programmatically or load from YAML.

## RuntimeConfig

```java
record RuntimeConfig(
    List<LlmConfig> llm,        // at least one required
    List<McpConfig> mcp,
    ComposioConfig composio,
    WorkerConfig agentRunner,
    List<AgentConfig> agents,
    List<SkillConfig> skills,
    List<PlanConfig> plans
)
```

Build with the fluent API:

```java
var config = RuntimeConfig.builder()
        .llm(LlmConfig.builder().apiKey(apiKey).build())
        .worker(WorkerConfig.builder().maxTurns(20).build())
        .agent(AgentConfig.forCatalog("agent.researcher.v1", "researcher", "...", "default"))
        .skill(SkillConfig.forCatalog("skill.citations.v1", "citations", "Always cite sources"))
        .build();
```

> **External IDs required.** Any `AgentConfig`, `SkillConfig`, or `PlanConfig` registered via `RuntimeConfig` or the Agentican fluent builder must have an `externalId`. See [External IDs](#external-ids).

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
        .agent(AgentConfig.builder()
                .externalId("agent.classifier.v1")
                .name("classifier").role("...").llm("fast")
                .build())
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

LLM calls are automatically retried on transient failures (rate limits, server errors, network issues) with exponential backoff and jitter. This is handled by `RetryingLlmClient`, which wraps every LLM client at construction time — all callers (agent runner, planner, knowledge extractor) get retries automatically.

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
        System.out.print(token);
    }
}
```

## AgentConfig

```java
record AgentConfig(
    String id,          // internal UUID, auto-generated
    String name,
    String role,
    String llm,
    String externalId   // required for config/builder agents
)
```

```java
AgentConfig.forCatalog("agent.researcher.v1", "researcher",
        "Expert at finding and synthesizing information", "default");

AgentConfig.builder()
        .externalId("agent.researcher.v1")
        .name("researcher")
        .role("Expert at finding and synthesizing information")
        .llm("default")                // optional; defaults to the "default" LLM
        .build();
```

Agents from config are pre-registered when Agentican starts. They can be referenced by name (or id) in plan steps.

## SkillConfig

Skills are reusable instruction blocks that plan steps can activate by id/name. They live in the top-level `RuntimeConfig.skills` list — they are not nested inside `AgentConfig`.

```java
record SkillConfig(String id, String name, String instructions, String externalId)
```

```java
SkillConfig.forCatalog("skill.citations.v1", "citations", "Always include source URLs");
```

## PlanConfig

Pre-built plans you want registered at boot. Each needs an `externalId`.

```java
var plan = new PlanConfig(
        "research-and-summarize",                      // name
        "Research a topic and summarize it",           // description
        List.of(new PlanConfig.PlanParamConfig("topic", "Topic to research", "AI", true)),
        List.of(/* PlanStepConfig entries */),
        "plan.research-and-summarize.v1");             // externalId
```

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

Multiple MCP servers can be registered. Each is accessed via its `slug`. The framework tries Streamable HTTP transport first, then falls back to SSE.

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
  - externalId: agent.researcher.v1
    name: researcher
    role: Expert researcher who finds and synthesizes information

  - externalId: agent.writer.v1
    name: writer
    role: Documentation specialist with clear, concise writing style

skills:
  - externalId: skill.citations.v1
    name: citations
    instructions: Always include source URLs

plans: []
```

```java
var config = RuntimeConfig.load(Path.of("agentican.yml"));
```

Environment variables in `${VAR}` form are resolved at load time.

## Agentican Builder

The `Agentican.builder()` accepts the runtime config plus optional overrides:

```java
Agentican.builder()
        .config(config)                             // required

        // Catalog entries (parity with RuntimeConfig's lists):
        .agent(AgentConfig.forCatalog(...))
        .skill(SkillConfig.forCatalog(...))
        .plan(planConfig)
        .mcp(McpConfig.builder()...build())
        .composio(ComposioConfig.builder()...build())

        // LLM + toolkits:
        .llm("name", llmClient)                     // pre-built LLM client
        .toolkit("slug", toolkit)                   // custom toolkit

        // Registry overrides (default: in-memory):
        .agentRegistry(myAgentRegistry)
        .skillRegistry(mySkillRegistry)
        .planRegistry(myPlanRegistry)

        // Stores + coordination:
        .hitlManager(hitlManager)                   // default: logging notifier
        .taskStateStore(taskStateStore)             // default: in-memory
        .knowledgeStore(knowledgeStore)             // default: in-memory

        // Observability + extension points:
        .llmDecorator(llmDecorator)
        .taskDecorator(taskDecorator)
        .stepListener(listener)
        .taskExecutor(executor)
        .build();
```

> Agents, skills, and plans supplied via both `RuntimeConfig` and the builder are merged. Each still must carry an `externalId` — the validation runs for both sources.

### Extension Points

| Interface | Purpose |
|---|---|
| `TaskListener` | Receives lifecycle events for tasks, steps, runs, turns, messages, responses, tool calls, and HITL. Used by OTel for span creation and metrics for counters. |
| `LlmClientDecorator` | Wraps every config-built `LlmClient`. Used by metrics (counters/timers) and OTel (LLM call spans). |
| `TaskDecorator` | Wraps the `Supplier` passed to `CompletableFuture.supplyAsync()` for each task. Used by OTel to propagate trace context to virtual threads. Supports `snapshot()` for step-level context capture. |
| `AgentRegistry` / `SkillRegistry` / `PlanRegistry` | Registry interfaces; supply a persistent backend via `.agentRegistry(...)` etc. Each exposes a `seed(...)` hook the framework calls once at boot. |

### Pre-built LLM Clients

If you want to inject your own `LlmClient` (for testing, custom providers, or to wrap with logging/caching), use `.llm(name, llmClient)`. This takes precedence over `LlmConfig` entries with the same name.

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

If not provided, Agentican creates a `MemTaskStateStore`. Implement your own for durable storage — the `TaskStateStore` interface uses granular mutation methods (`taskStarted()`, `stepStarted()`, `runStarted()`, `turnStarted()`, `messageSent()`, etc.) plus query methods `load(taskId)` and `list()`.

```java
Agentican.builder()
        .config(config)
        .taskStateStore(new DatabaseTaskStateStore(dataSource))
        .build();
```

## External IDs

`AgentConfig`, `SkillConfig`, `PlanConfig`, and `Plan` all carry an optional `externalId` separate from their internal UUID `id`. The `externalId` is a stable business key that a persistent catalog upserts on, so redeploys don't duplicate rows.

Anything registered through `RuntimeConfig` or the Agentican fluent builder **must** set an `externalId`. `Agentican.build()` throws `IllegalStateException` on any missing one:

```
skill 'citations' is missing an externalId. Config-file and fluent-builder
skills must declare a stable externalId so the catalog can upsert
consistently across deploys.
```

Use the `forCatalog(externalId, ...)` factories or the `externalId(...)` builder method:

```java
AgentConfig.forCatalog("agent.researcher.v1", "researcher", "Expert researcher", "default");
SkillConfig.forCatalog("skill.citations.v1",  "citations",  "Always cite sources");
Plan.withExternalId("plan.research.v1", "research", "...", params, steps);
```

Planner-created agents, skills, and plans have no `externalId` — they're ephemeral, scoped to the run that produced them.

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
