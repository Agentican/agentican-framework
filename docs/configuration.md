# Configuration

Configure Agentican via the fluent builder. The builder splits into two orthogonal axes:

- **Configuration** — framework wiring (LLMs, MCP, Composio, worker, strict). Source is API or YAML.
- **Registry** — catalog (agents, skills, workflows). Source is API or YAML.

Both can be sourced independently, giving four combinations: api/api, yaml/yaml, api/yaml, yaml/api.

## Builder shape

```java
Agentican.builder()
    // Java-object-only setters (no YAML equivalent)
    .toolkit(slug, toolkit)
    .hitlManager(...)
    .knowledgeStore(...)
    .workflowRunStore(...)
    .agentRegistry(impl) / .skillRegistry(impl) / .workflowRegistry(impl)
    .llmDecorator(...) / .workflowRunDecorator(...) / .workflowRunListener(...)
    .taskExecutor(...)
    .codeStep(slug, In.class, Out.class, executor)
    .vectorIndex(index)
    .llm("name", inMemoryLlmClient)

    // Framework-wiring config (api XOR yaml)
    .configuration().api()
        .llm(LlmConfig)
        .mcp(McpConfig)
        .composio(ComposioConfig)
        .worker(WorkerConfig)
        .strict()
        .end()
    .configuration().yaml()
        .path(Path) | .classpath(String) | .config(EngineConfig)
        .end()

    // Catalog (api XOR yaml)
    .registry().api()
        .agent(AgentConfig)
        .skill(SkillConfig)
        .workflow(WorkflowConfig)
        .end()
    .registry().yaml()
        .path(Path) | .classpath(String) | .config(CatalogConfig)
        .end()

    .build();
```

Each sub-builder exits via `.end()`, returning the outer `Builder`. The `.api()` and `.yaml()` sub-builders are mutually exclusive within a single `.configuration()` (or `.registry()`).

### Common patterns

**Programmatic everything:**

```java
try (var agentican = Agentican.builder()
        .configuration().api()
            .llm(LlmConfig.builder().apiKey(apiKey).build())
            .worker(WorkerConfig.builder().maxTurns(20).build())
            .end()
        .registry().api()
            .agent(AgentConfig.builder().id("researcher").name("researcher").role("...").llm("default").build())
            .skill(SkillConfig.builder().id("citations").name("citations").instructions("Always cite sources").build())
            .end()
        .build()) {
    // use agentican
}
```

**Two YAML files (recommended):**

```java
try (var agentican = Agentican.builder()
        .configuration().yaml().classpath("engine.yaml").end()
        .registry().yaml().classpath("catalog.yaml").end()
        .build()) {
    // use agentican
}
```

`engine.yaml` carries `llm:`, `mcp:`, `composio:`, `agentRunner:`, `strict:`. `catalog.yaml` carries `agents:`, `skills:`, `workflows:`. Each file deserializes into its own typed record (`EngineConfig` and `CatalogConfig`); engine YAML and catalog YAML have disjoint top-level keys.

**One YAML file for both axes:** point both at the same path. `EngineConfig` and `CatalogConfig` ignore each other's top-level keys, so a mixed file works:

```java
try (var agentican = Agentican.builder()
        .configuration().yaml().classpath("agentican.yaml").end()
        .registry().yaml().classpath("agentican.yaml").end()
        .build()) {
    // use agentican
}
```

**Mixed — YAML for engine, programmatic catalog:**

```java
try (var agentican = Agentican.builder()
        .configuration().yaml().classpath("engine.yaml").end()
        .registry().api()
            .workflow(WorkflowConfig.builder()...build())
            .end()
        .build()) {
    // use agentican
}
```

### EngineConfig

Framework wiring — what's needed to boot the runtime:

```java
record EngineConfig(
    List<LlmConfig> llm,
    List<McpConfig> mcp,
    ComposioConfig composio,
    WorkerConfig agentRunner,
    boolean strict
)

// Load from YAML programmatically:
var engine = EngineConfig.load(Path.of("engine.yaml"));
```

### CatalogConfig

Catalog data — what gets seeded into the registries:

```java
record CatalogConfig(
    List<AgentConfig> agents,
    List<SkillConfig> skills,
    List<WorkflowConfig> workflows
)

// Load from YAML programmatically:
var catalog = CatalogConfig.load(Path.of("catalog.yaml"));
```

## LlmConfig

```java
LlmConfig.builder()
        .name("default")          // optional, defaults to "default"
        .provider("anthropic")    // see "Supported providers" below
        .model("claude-sonnet-4-5")
        .apiKey(apiKey)           // required for every provider except "bedrock"
        .secretKey(null)          // only paired with apiKey when provider="bedrock"
        .region(null)             // only used for provider="bedrock" today
        .maxTokens(16384)
        .temperature(0.7)         // optional — null/unset means provider default
        .baseUrl(null)            // only used when provider is "openai-compatible"
        .build()
```

### Supported providers

| `provider` | Routed through | Default base URL | Built-in web search | Notes |
|---|---|---|---|---|
| `anthropic` (default) | `AnthropicLlmClient` | api.anthropic.com | ✓ `web_search` + `web_fetch` | Prompt caching via explicit `cache_control`. |
| `openai` | `OpenAiLlmClient` (Responses API) | api.openai.com | ✓ `web_search` | Auto prompt caching; `cacheReadTokens` reported. |
| `groq` | `OpenAiLlmClient` (Responses API) | api.groq.com/openai/v1 | ✓ only on `openai/gpt-oss-*` models (auto-added as `browser_search`) | Other Groq-hosted models (Llama, Qwen, DeepSeek) get no built-in search. Cached tokens reported. |
| `gemini` | `GeminiLlmClient` | generativelanguage.googleapis.com | ✓ `google_search` grounding | Needs Gemini 2.0+ models. |
| `sambanova` | `OpenAiCompatibleLlmClient` (Chat Completions) | api.sambanova.ai/v1 | ✗ | Reports `cached_tokens`. |
| `together` | `OpenAiCompatibleLlmClient` | api.together.xyz/v1 | ✗ | Cached-tokens undocumented; defaults to 0. |
| `fireworks` | `OpenAiCompatibleLlmClient` | api.fireworks.ai/inference/v1 | ✗ | Cached-tokens not surfaced. |
| `bedrock` | `BedrockLlmClient` (Converse API) | **AWS SDK default; `region` on `LlmConfig`** | ✗ | Claude, Llama, Nova, Mistral, DeepSeek, Cohere, AI21 — all through one API. Auth via AWS credentials chain. |
| `openai-compatible` | `OpenAiCompatibleLlmClient` | **`baseUrl` required on `LlmConfig`** | ✗ | Escape hatch for self-hosted / proxied endpoints (Ollama, vLLM, LiteLLM, LocalAI, corporate proxies). |

### AWS Bedrock (`bedrock`)

Bedrock is an AWS service, so auth is SigV4 against your AWS credentials — not a bearer API key. `apiKey` is **optional**; if unset, the framework uses the AWS SDK's `DefaultCredentialsProvider`, which walks the standard chain (env vars `AWS_ACCESS_KEY_ID` / `AWS_SECRET_ACCESS_KEY` / `AWS_SESSION_TOKEN`, `~/.aws/credentials`, IAM role on EC2 / ECS / Lambda, etc.). If you do set `apiKey`, pair it with `secretKey` — the framework will use `StaticCredentialsProvider`:

```java
// Zero-config — relies on the default AWS credentials chain.
LlmConfig.builder()
        .provider("bedrock")
        .region("us-east-1")
        .model("anthropic.claude-sonnet-4-5-20250929-v1:0")
        .build();

// Explicit static credentials — rare in production, common in tests.
LlmConfig.builder()
        .provider("bedrock")
        .region("us-east-1")
        .apiKey(System.getenv("AWS_ACCESS_KEY_ID"))
        .secretKey(System.getenv("AWS_SECRET_ACCESS_KEY"))
        .model("anthropic.claude-sonnet-4-5-20250929-v1:0")
        .build();
```

`region` is optional; when unset the AWS SDK's region chain resolves it (`AWS_REGION`, `AWS_DEFAULT_REGION`, profile). `model` **is required** for `provider=bedrock` — Bedrock model IDs are namespaced (`anthropic.…`, `meta.…`, `amazon.…`) so the framework's default `claude-sonnet-4-5` isn't a valid Bedrock ID and the compact constructor rejects it.

Routes through the Converse API, which is Bedrock's unified cross-model surface — so Claude, Llama, Nova, Mistral, Cohere, DeepSeek, and AI21 models are all reachable with the same `provider=bedrock` and just a different `model` ID.

### Self-hosted endpoints (`openai-compatible`)

To point the framework at any OpenAI-compatible Chat Completions endpoint — Ollama on localhost, a vLLM deployment, LiteLLM proxying across providers, a corporate OpenAI proxy — set `provider` to `openai-compatible` and supply `baseUrl`:

```java
LlmConfig.builder()
        .provider("openai-compatible")
        .baseUrl("http://localhost:11434/v1")   // Ollama default
        .apiKey("ollama")                       // Ollama ignores; any non-blank string works
        .model("llama3.3:70b")
        .build();
```

The framework validates that `baseUrl` is non-blank when `provider == "openai-compatible"` and throws at construction time if it isn't.

### Temperature

`temperature` is optional. Left `null`/unset, each provider uses its own default (Anthropic 1.0, OpenAI 1.0, Gemini model-dependent, Chat-Completions backends typically 0.7). Pass a `Double` in whatever range the provider accepts. Out-of-range values surface as provider-side errors.

You can register multiple LLMs — mixing providers freely — and assign them to specific agents:

```java
Agentican.builder()
        .configuration().api()
            .llm(LlmConfig.builder().name("default").apiKey(anthropicKey).model("claude-sonnet-4-5").build())
            .llm(LlmConfig.builder().name("fast").provider("openai").apiKey(openaiKey).model("gpt-4o-mini").build())
            .llm(LlmConfig.builder().name("grounded").provider("gemini").apiKey(geminiKey).model("gemini-2.5-flash").build())
            .end()
        .registry().api()
            .agent(AgentConfig.builder()
                    .id("classifier").name("classifier").role("...").llm("fast")
                    .build())
            .end()
        .build();
```

### Provider differences at a glance

| Capability | Anthropic | OpenAI | Gemini | SambaNova |
|---|---|---|---|---|
| Built-in web search | On by default (`webSearchRequests` count). | On by default via Responses API `web_search` tool (same counter). | On by default via the `google_search` grounding tool; the count of search queries issued lands in `webSearchRequests`. Requires Gemini 2.0+ models. | Not available. SambaNova hosts OSS models only; bring your own search tool if you need grounding. `webSearchRequests` is always `0`. |
| Built-in web fetch | On by default. | Not available. | Not available as a separate tool (grounding brings inline citations instead). | Not available. |
| Prompt caching | Explicit `cache_control` blocks; cache hits reported as `cacheReadTokens`. | Automatic once a prompt exceeds the model's caching threshold; reported as `cacheReadTokens`. `cacheWriteTokens` is always `0` (no write tier). | Explicit: create a `cachedContents` resource ahead of time and reference it. Not exposed in `LlmConfig` yet — a future follow-on. Cached tokens are reported as `cacheReadTokens` if the underlying client wiring uses a cached-content handle. | Automatic; cache hits reported as `cacheReadTokens` in the same shape as OpenAI. `cacheWriteTokens` always `0`. |
| Function-call arguments on the wire | JSON object. | JSON string (parsed to `Map` before reaching your code). | JSON object. | JSON string (parsed to `Map` before reaching your code). |
| Max tokens field | `max_tokens`. | `max_output_tokens`. | `maxOutputTokens`. The framework's `maxTokens` config maps to whichever the provider expects. | `max_completion_tokens`. |
| Typical models | `claude-sonnet-4-5`, `claude-opus-4-5` | `gpt-4o`, `gpt-4o-mini`, `o4-mini` | `gemini-2.5-flash`, `gemini-2.5-pro` | `Meta-Llama-3.3-70B-Instruct`, `DeepSeek-V3.2`, `Qwen3-235B`, `gpt-oss-120b` |

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

On exhaustion, the last exception is rethrown (wrapped in a `RuntimeException` if it was checked).

### LLM Streaming

`LlmClient` supports optional token streaming via `sendStreaming()`:

```java
default LlmResponse sendStreaming(LlmRequest request, Consumer<String> onToken) {
    // Default: falls back to send(), fires callback once with complete text
}
```

LLM client implementations can override this to stream tokens incrementally. The `SmacAgentRunner` calls `sendStreaming()` automatically, passing each token to `WorkflowRunListener.onToken()`.

Observe tokens in real time:

```java
public class MyListener implements WorkflowRunListener {
    @Override
    public void onToken(String taskId, String turnId, String token) {
        System.out.print(token);
    }
}
```

## AgentConfig

```java
record AgentConfig(
    String id,          // required — stable identifier; recommend slug-style (e.g. "researcher")
    String name,        // unique within AgentRegistry
    String role,
    String llm,         // LLM name from LlmConfig
    String runner,      // optional: "smac" or "react"
    Integer maxTurns,   // optional override
    Duration timeout    // optional override
)
```

```java
AgentConfig.builder()
        .id("researcher")
        .name("researcher")
        .role("Expert at finding and synthesizing information")
        .llm("default")                // optional; defaults to the "default" LLM
        .build();
```

Agents from config are pre-registered when Agentican starts. They can be referenced by name in workflow steps. `id` is required — pick a stable slug (`researcher`, `senior-writer`); the framework throws `IllegalArgumentException` on null/blank ids.

## SkillConfig

Skills are reusable instruction blocks that workflow steps can activate by name. They live in the top-level `CatalogConfig.skills` list — they are not nested inside `AgentConfig`.

```java
record SkillConfig(String id, String name, String instructions)  // id is required
```

```java
SkillConfig.builder()
        .id("citations")
        .name("citations")
        .instructions("Always include source URLs")
        .build();
```

## WorkflowConfig

Pre-built workflows you want registered at boot.

```java
var workflow = new WorkflowConfig(
        "research-and-summarize",                                                       // id (required)
        "Research and summarize",                                                       // name
        "Research a topic and summarize it",                                            // description
        List.of(new WorkflowConfig.PlanParamConfig("topic", "Topic to research", "AI", true)),
        List.of(/* PlanStepConfig entries */),
        null);                                                                          // outputStep — set for typed Workflow<P, R>
```

`WorkflowDefinition.builder(id, name)` similarly takes both arguments at the call site.

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

**Engine YAML** — `engine.yaml`:

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
```

**Catalog YAML** — `catalog.yaml`:

```yaml
agents:
  - id: researcher
    name: researcher
    role: Expert researcher who finds and synthesizes information

  - id: writer
    name: writer
    role: Documentation specialist with clear, concise writing style

skills:
  - id: citations
    name: citations
    instructions: Always include source URLs

workflows: []
```

Every agent, skill, and workflow entry must declare an `id`. Use stable, slug-style ids (lowercase alphanumeric + dashes); they're how the registry, audit log, and persistent state stores reference catalog entries across runs. The framework rejects entries with missing or blank ids at load time.

```java
var engine = EngineConfig.load(Path.of("engine.yaml"));
var catalog = CatalogConfig.load(Path.of("catalog.yaml"));
```

Environment variables in `${VAR}` form are resolved at load time.

To wire both files into a Builder:

```java
Agentican.builder()
        .configuration().yaml().path(Path.of("engine.yaml")).end()
        .registry().yaml().path(Path.of("catalog.yaml")).end()
        .build();
```

### Extension Points

| Interface | Purpose |
|---|---|
| `WorkflowRunListener` | Receives lifecycle events for workflow runs, steps, runs, turns, messages, responses, tool calls, and HITL. Used by OTel for span creation and metrics for counters. |
| `LlmClientDecorator` | Wraps every config-built `LlmClient`. Used by metrics (counters/timers) and OTel (LLM call spans). |
| `WorkflowRunDecorator` | Wraps the `Supplier` passed to `CompletableFuture.supplyAsync()` for each workflow run. Used by OTel to propagate trace context to virtual threads. Supports `snapshot()` for step-level context capture. |
| `AgentRegistry` / `SkillRegistry` / `WorkflowRegistry` | Registry interfaces; supply a persistent backend via `.agentRegistry(impl)` etc. on the Builder. Each exposes a `seed()` hook the framework calls once at boot. |

### Pre-built LLM Clients

To inject your own `LlmClient` (for testing, custom providers, or to wrap with logging/caching), use `.llm(name, llmClient)` on the Builder. This takes precedence over `LlmConfig` entries with the same name.

```java
LlmClient cachedClient = LlmClient.withLogging(myCustomClient);

Agentican.builder()
        .llm("default", cachedClient)
        .build();
```

### Custom HitlManager

If not provided, Agentican creates one with a logging notifier (auto-approves, logs each checkpoint).

```java
Agentican.builder()
        .hitlManager(new HitlManager(myNotifier, Duration.ofHours(2)))
        .build();
```

### Custom WorkflowRunStore

If not provided, Agentican creates an `InMemoryWfRunStore`. Implement your own for durable storage — the `WorkflowRunStore` interface uses granular mutation methods (`taskStarted()`, `stepStarted()`, `runStarted()`, `turnStarted()`, `messageSent()`, etc.) plus query methods `load(taskId)` and `list()`.

```java
Agentican.builder()
        .workflowRunStore(new DatabaseWorkflowRunStore(dataSource))
        .build();
```

## Identity

Agents, skills, and workflows are identified by **name** within their respective registries. Names must be unique per registry and are the canonical reference everywhere — YAML files, the planner's reuse output, workflow steps' `agentName` field, the Quarkus `@AgenticanWorkflow(name = "...")` qualifier. The `id` field is required at construction time and is what persistence stores key on.

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
try (var agentican = Agentican.builder(runtimeConfig).build()) {
    // ...
} // automatically closes the virtual thread executor and toolkits
```

`close()` shuts down the task executor (waiting for in-flight tasks to finish) and closes any toolkits that implement `AutoCloseable` (e.g., MCP connections).
