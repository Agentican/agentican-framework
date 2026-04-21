# Tools & Toolkits

Toolkits are how agents interact with the outside world — calling APIs, querying databases, sending messages, anything you want.

## The Toolkit Interface

```java
public interface Toolkit {

    List<Tool> tools();

    boolean handles(String toolName);

    String execute(String toolName, Map<String, Object> arguments) throws Exception;

    default List<ToolDefinition> toolDefinitions() {
        return tools().stream().map(Tool::toDefinition).toList();
    }

    default HitlType hitlType(String toolName) {
        return tools().stream()
                .filter(t -> t.name().equals(toolName))
                .map(Tool::hitlType)
                .findFirst()
                .orElse(HitlType.NONE);
    }
}
```

A toolkit is a bag of related tools. Each `Tool` has a name, description, JSON schema for its inputs, and an optional HITL type.

## Built-in Toolkits

### ScratchpadToolkit

Task-scoped key/value memory across turns. Always available.

Tools:
- `store(key, description, details)` — save a piece of information
- `recall(key)` — retrieve a previously stored entry
- `recall_all()` — list all entries

The scratchpad is shared across all agents in the same task. Data stored by one agent (e.g., structured citations) can be recalled by another agent in a later step. Sub-tasks (loop iterations, branch paths) inherit the parent task's scratchpad.

Use this when the agent needs to remember something across many tool calls (e.g., a list of items it's processing).

### AskQuestionToolkit

Lets the agent pause and ask the user a question. Always available.

Tool:
- `ASK_QUESTION(question, context)` — pause execution and surface the question via HITL

When the agent calls this, the framework creates a `QUESTION` checkpoint. Your `HitlNotifier` receives the question; you respond with `HitlResponse.approve("user's answer")`. The agent resumes with the answer as the tool result.

See [Human in the Loop](hitl.md) for details.

## Custom Toolkits

To add your own tools, implement `Toolkit`:

```java
import ai.agentican.framework.tools.*;
import com.fasterxml.jackson.databind.ObjectMapper;

public class WeatherToolkit implements Toolkit {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final Tool getWeather = new ToolRecord(
            "get_weather",
            "Get current weather for a city",
            Map.of(
                "city", Map.of("type", "string", "description", "City name")
            ),
            List.of("city"),
            HitlType.NONE
    );

    @Override
    public List<Tool> tools() {
        return List.of(getWeather);
    }

    @Override
    public boolean handles(String toolName) {
        return "get_weather".equals(toolName);
    }

    @Override
    public String execute(String toolName, Map<String, Object> arguments) throws Exception {

        var city = arguments.get("city").toString();
        var weather = fetchWeatherFromApi(city);

        return JSON.writeValueAsString(weather);
    }
}
```

Register it with the builder:

```java
try (var agentican = AgenticanRuntime.builder()
        .toolkit("weather", new WeatherToolkit())
        .build()) {

    agentican.run("What's the weather in Seattle?").result();
}
```

The planner sees `weather` as an available toolkit slug. When it creates a step that needs weather data, it includes `weather` in that step's `toolkits` list, and the agent gets `get_weather` in its tool list.

### Tool Definitions

`Tool` is an interface; the easiest implementation is `ToolRecord`:

```java
new ToolRecord(
    String name,                    // tool name (must be unique within toolkit)
    String description,             // what it does — important, the LLM uses this
    Map<String, Object> properties, // JSON schema properties
    List<String> required,          // required property names
    HitlType hitlType               // NONE, APPROVAL, or QUESTION
)
```

The properties map follows JSON Schema:

```java
Map.of(
    "city",   Map.of("type", "string", "description", "City name"),
    "units",  Map.of("type", "string", "enum", List.of("celsius", "fahrenheit"))
)
```

### Marking Tools for HITL

Set `HitlType.APPROVAL` to require human approval before execution:

```java
new ToolRecord(
    "send_email", "Send an email to a recipient",
    Map.of("to", ..., "subject", ..., "body", ...),
    List.of("to", "subject", "body"),
    HitlType.APPROVAL  // ← user will be prompted before this runs
)
```

The framework will detect the call, create a checkpoint, and pause until your `HitlNotifier` responds.

## Composio Integration

[Composio](https://composio.dev) provides 200+ pre-built SaaS integrations (Notion, GitHub, Slack, Google Workspace, Linear, Jira, etc.). Agentican wraps them automatically.

```java
AgenticanRuntime.builder()
        .llm(LlmConfig.builder().apiKey(anthropicKey).build())
        .composio(ComposioConfig.builder()
                .apiKey(composioApiKey)
                .userId("user@example.com")
                .build())
        .build();
```

On startup, Agentican calls Composio's API to discover the user's connected toolkits. Each is registered by its slug (e.g., `notion`, `github`, `slack`). Tasks can reference them in step `toolkits` lists.

## MCP Integration

[Model Context Protocol](https://modelcontextprotocol.io) is an open standard for tool servers. Agentican supports any MCP-compliant server.

```java
AgenticanRuntime.builder()
        .llm(...)
        .mcp(McpConfig.builder()
                .slug("filesystem")
                .name("Local Filesystem")
                .url("http://localhost:3000")
                .build())
        .mcp(McpConfig.builder()
                .slug("github")
                .name("GitHub MCP")
                .url("https://github-mcp.example.com")
                .header("Authorization", "Bearer " + token)
                .build())
        .build();
```

MCP toolkits are registered by `slug`. The framework supports both Streamable HTTP and SSE transports automatically (it tries HTTP first, falls back to SSE).

## Tool Scoping

A step's `tools` field lists the tool **names** the agent is allowed to call for that step. The registry resolves each name to its owning toolkit at dispatch time:

```java
PlanStepAgent.builder("create-page")
    .agent("documentation-specialist")
    .instructions("Create a Notion page")
    .tools(List.of("create_page", "append_block"))  // ← explicit tool names
    .build();
```

Equivalent fluent forms on the `PlanConfig` step builder:

```java
.step("create-page", s -> s.agent("documentation-specialist")
    .instructions("Create a Notion page")
    .tools("create_page", "append_block"))
```

The agent only sees the listed tools (plus the always-available scratchpad and ASK_QUESTION). Keeping the list tight focuses the LLM's tool-selection and reduces hallucinated calls.

> **Tool name collisions:** If two toolkits define a tool with the same name and both are in scope, `ToolkitRegistry.scopeForStep` keeps the first match (insertion order). Avoid collisions by renaming or picking tool names that won't overlap across your registered toolkits.

## Tool Execution

When the LLM returns a tool call, the runner:

1. Looks up the toolkit by tool name
2. Checks `hitlType()` — if APPROVAL/QUESTION, suspends for HITL
3. Otherwise, calls `toolkit.execute(toolName, args)` directly
4. Wraps the result in a `ToolResult`
5. Stores it in the agent's scratchpad
6. Includes it in the next LLM turn's user message

Multiple tool calls in one turn execute in parallel using `StructuredTaskScope`. If any throws, the framework catches the exception and returns a JSON error result so the agent can react.

## Next Steps

- [Human in the Loop](hitl.md) — approval and question flows
- [Configuration](configuration.md) — Composio and MCP config reference
- [Examples](examples.md) — toolkit recipes
