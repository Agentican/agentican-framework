# Getting Started

This guide walks you through installing Agentican, configuring it, and running your first agentic task.

## Requirements

- **Java 25 or later**
- **Maven 3.9+**
- **Anthropic API key** (Agentican uses Claude as the default LLM)

## Installation

Agentican is published to [Maven Central](https://central.sonatype.com/namespace/ai.agentican). Add the core dependency to your `pom.xml` — no custom repository configuration required:

```xml
<dependency>
    <groupId>ai.agentican</groupId>
    <artifactId>agentican-framework-core</artifactId>
    <version>0.1.0-alpha.1</version>
</dependency>
```

On Quarkus? Use [`agentican-quarkus-runtime`](quarkus/getting-started.md) instead — it brings the core framework in transitively and adds CDI, config binding, and lifecycle wiring.

## Hello World

Create a simple program that delegates a task to Agentican:

```java
import ai.agentican.framework.Agentican;
import ai.agentican.framework.config.LlmConfig;

public class Hello {

    public static void main(String[] args) {

        try (var agentican = Agentican.builder()
                .configuration().api()
                    .llm().apiKey(System.getenv("ANTHROPIC_API_KEY")).end()
                    .end()
                .build()) {

            var run = agentican.run("Explain quantum entanglement in 3 sentences.");

            System.out.println(run.await());
        }
    }
}
```

Set your API key and run it:

```bash
export ANTHROPIC_API_KEY=sk-ant-...
mvn compile exec:java -Dexec.mainClass=Hello
```

### Using OpenAI instead

Swap the `LlmConfig` for an OpenAI-backed one — everything else is identical:

```java
try (var agentican = Agentican.builder()
        .configuration().api()
            .llm()
                .provider("openai")
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .model("gpt-4o-mini")
                .end()
            .end()
        .build()) { /* use agentican */ }
```

### Using Google Gemini

Same shape, `provider = "gemini"`. Google Search grounding is on by default; pick any Gemini 2.0+ model:

```java
try (var agentican = Agentican.builder()
        .configuration().api()
            .llm()
                .provider("gemini")
                .apiKey(System.getenv("GOOGLE_API_KEY"))
                .model("gemini-2.5-flash")
                .end()
            .end()
        .build()) { /* use agentican */ }
```

### Using OSS-hosted providers

The framework ships native support for four OSS-focused providers. All four take an OpenAI-shaped `LlmConfig`; only the `provider` and `model` strings change.

```java
// Groq — routes through the Responses API; built-in browser_search
// kicks in automatically on openai/gpt-oss-* models.
.provider("groq").model("llama-3.3-70b-versatile")
.provider("groq").model("openai/gpt-oss-120b")

// SambaNova — reports cached_tokens; OSS model zoo.
.provider("sambanova").model("Meta-Llama-3.3-70B-Instruct")

// Together — broadest coverage (chat, vision, embeddings, speech).
.provider("together").model("meta-llama/Llama-3.3-70B-Instruct-Turbo")

// Fireworks — OpenAI-compatible Chat Completions.
.provider("fireworks").model("accounts/fireworks/models/llama-v3p3-70b-instruct")
```

### Using AWS Bedrock

Bedrock's Converse API serves Claude, Llama, Nova, Mistral, Cohere, and DeepSeek through one unified request/response shape. Auth comes from your AWS credentials (env vars, `~/.aws/credentials`, IAM role — whatever the AWS SDK's default chain picks up):

```java
try (var agentican = Agentican.builder()
        .configuration().api()
            .llm()
                .provider("bedrock")
                .region("us-east-1")
                .model("anthropic.claude-sonnet-4-5-20250929-v1:0")
                .end()
            .end()
        .build()) { /* use agentican */ }
```

For static credentials — e.g. in tests — pair `apiKey` and `secretKey`:

```java
.provider("bedrock")
.region("us-east-1")
.apiKey(System.getenv("AWS_ACCESS_KEY_ID"))
.secretKey(System.getenv("AWS_SECRET_ACCESS_KEY"))
.model("anthropic.claude-sonnet-4-5-20250929-v1:0")
```

### Using a self-hosted OpenAI-compatible endpoint

Point the framework at Ollama, vLLM, LiteLLM, LocalAI, or any corporate proxy with the `openai-compatible` provider plus an explicit `baseUrl`:

```java
try (var agentican = Agentican.builder()
        .configuration().api()
            .llm()
                .provider("openai-compatible")
                .baseUrl("http://localhost:11434/v1")
                .apiKey("ollama")          // Ollama ignores; any non-blank string works
                .model("llama3.3:70b")
                .end()
            .end()
        .build()) { /* use agentican */ }
```

See [Configuration → Supported providers](configuration.md#supported-providers) for what's the same and what's not across providers.

## What Just Happened?

When you called `agentican.run("Explain quantum entanglement...")`, the framework:

1. **Planned** the task — `WorkflowPlannerAgent` decided whether to reuse a cataloged `WorkflowDefinition` or create a new one from the description
2. **Built** any agents the planner introduced via `AgentFactory`
3. **Refined** each step's instructions with available tool context (create path only)
4. **Executed** the workflow on virtual threads, returning a `WorkflowRun<String>`
5. **Returned** the typed output when you called `run.await()`

For a simple explanation task, the planner created a single step with an LLM call. For multi-step tasks, it would compose specialized agents that work in parallel where possible.

## Async Execution

`run()` returns a `WorkflowRun<String>` immediately — execution happens on a virtual thread. You can:

```java
var run = agentican.run("Long running task...");

// Don't block — check later
if (!run.isDone()) {
    // Do other work
}

// Block when ready and get the typed output (String here)
var output = run.await();

// Or use CompletableFuture for the typed output
run.future().thenAccept(output -> {
    System.out.println(output);
});

// Or get the full execution struct (status, step outputs, token usage)
var result = run.untypedResult();

// Cancel if needed
run.cancel();
```

## Adding Tools

Out of the box, agents can search the web and fetch content (built into Claude). To give them more powers, register a toolkit:

```java
var myToolkit = new MyCustomToolkit();

try (var agentican = Agentican.builder()
        .toolkit("my-tools", myToolkit)
        .configuration().api()
            .llm().apiKey(apiKey).end()
            .end()
        .build()) {

    agentican.run("Use my tools to do the thing").await();
}
```

See [Tools & Toolkits](tools.md) for how to write a toolkit, and how to use the built-in Composio and MCP integrations for hundreds of pre-built tools.

## Human-in-the-Loop

For tasks that need human approval, register a `HitlManager`:

```java
var hitlManager = new HitlManager((mgr, checkpoint) -> {

    System.out.println("Approve? " + checkpoint.description());
    var line = new Scanner(System.in).nextLine();

    var response = line.equals("y")
            ? HitlResponse.approve()
            : HitlResponse.reject("User declined");

    mgr.respond(checkpoint.id(), response);
});

try (var agentican = Agentican.builder()
        .hitlManager(hitlManager)
        .configuration().api()
            .llm().apiKey(apiKey).end()
            .end()
        .build()) {

    agentican.run("Send an email summarizing today's standup").await();
}
```

See [Human in the Loop](hitl.md) for the full HITL model.

## Next Steps

- [Core Concepts](concepts.md) — understand the architecture
- [Tasks & Steps](tasks.md) — define workflows manually instead of via the planner
- [Agents](agents.md) — define specialized agents with skills
- [Tools & Toolkits](tools.md) — build custom toolkits
- [Examples](examples.md) — common patterns and recipes
