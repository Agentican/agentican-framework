# Examples

Common patterns and recipes for using Agentican.

## Simple Delegation

Plan and execute a task from natural language:

```java
var config = RuntimeConfig.builder()
        .llm(LlmConfig.builder().apiKey(apiKey).build())
        .build();

try (var agentican = Agentican.builder().config(config).build()) {

    var result = agentican.run("Summarize the latest news about quantum computing").result();

    System.out.println(result.lastOutput());
}
```

## Pre-defined Plan

Skip the planner and run a hand-built plan:

```java
var task = Plan.builder("research-and-summarize")
        .description("Research a topic and summarize")
        .param("topic", "What to research", "AI")
        .step("research", "researcher", "Research {{param.topic}} using web search")
        .step("summarize", "writer",
                "Summarize the research:\n\n{{step.research.output}}",
                List.of("research"))
        .build();

agentican.run(task, Map.of("topic", "fusion energy")).result();
```

## Custom Toolkit

Add a database query toolkit:

```java
public class DbToolkit implements Toolkit {

    private static final ObjectMapper JSON = new ObjectMapper();
    private final DataSource dataSource;

    public DbToolkit(DataSource ds) {
        this.dataSource = ds;
    }

    @Override
    public List<Tool> tools() {
        return List.of(new ToolRecord(
                "query_users",
                "Query the users table by status",
                Map.of("status", Map.of("type", "string",
                        "enum", List.of("active", "inactive", "pending"))),
                List.of("status"),
                HitlType.NONE
        ));
    }

    @Override
    public boolean handles(String toolName) {
        return "query_users".equals(toolName);
    }

    @Override
    public String execute(String toolName, Map<String, Object> args) throws Exception {

        var status = args.get("status").toString();

        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement("SELECT id, name FROM users WHERE status = ?")) {

            stmt.setString(1, status);
            var rs = stmt.executeQuery();

            var users = new ArrayList<Map<String, Object>>();

            while (rs.next())
                users.add(Map.of("id", rs.getInt("id"), "name", rs.getString("name")));

            return JSON.writeValueAsString(users);
        }
    }
}

// Register and use:
try (var agentican = Agentican.builder()
        .config(config)
        .toolkit("db", new DbToolkit(myDataSource))
        .build()) {

    agentican.run("How many active users do we have? List the first 5.").result();
}
```

## HITL Tool Approval (CLI)

Block execution of sensitive tools until the user approves:

```java
var hitlManager = new HitlManager((mgr, checkpoint) -> {

    if (checkpoint.type() == HitlCheckpointType.TOOL_CALL) {

        System.out.println("\n[APPROVAL NEEDED]");
        System.out.println("Tool: " + checkpoint.description());
        System.out.println("Args: " + checkpoint.content());
        System.out.print("Approve? (y/n): ");

        var input = new Scanner(System.in).nextLine();

        var response = input.equalsIgnoreCase("y")
                ? HitlResponse.approve()
                : HitlResponse.reject("User declined");

        mgr.respond(checkpoint.id(), response);
    }
});

try (var agentican = Agentican.builder()
        .config(config)
        .toolkit("email", new EmailToolkit())  // send_email is HitlType.APPROVAL
        .hitlManager(hitlManager)
        .build()) {

    agentican.run("Send a follow-up to john@example.com about Tuesday's meeting").result();
}
```

## HITL Tool Approval (REST)

For web apps, store checkpoints and respond via REST:

```java
@Component
public class HitlController {

    private final HitlManager hitlManager;
    private final Map<String, HitlCheckpoint> pending = new ConcurrentHashMap<>();

    public HitlController() {

        this.hitlManager = new HitlManager((mgr, checkpoint) -> {

            // Park the checkpoint — REST endpoint will resolve it
            pending.put(checkpoint.id(), checkpoint);

            // Notify the user (websocket, email, push, etc.)
            notifyUser(checkpoint);
        });
    }

    @GetMapping("/checkpoints")
    public List<HitlCheckpoint> list() {
        return List.copyOf(pending.values());
    }

    @PostMapping("/checkpoints/{id}/approve")
    public void approve(@PathVariable String id, @RequestBody(required = false) String feedback) {

        pending.remove(id);
        hitlManager.respond(id, HitlResponse.approve(feedback != null ? feedback : ""));
    }

    @PostMapping("/checkpoints/{id}/reject")
    public void reject(@PathVariable String id, @RequestBody String feedback) {

        pending.remove(id);
        hitlManager.respond(id, HitlResponse.reject(feedback));
    }
}
```

## Asking the User a Question

The agent can ask the user mid-workflow via the built-in `ASK_QUESTION` tool. The notifier handles `QUESTION` checkpoints:

```java
var hitlManager = new HitlManager((mgr, checkpoint) -> {

    if (checkpoint.type() == HitlCheckpointType.QUESTION) {

        System.out.println("\nAgent asks: " + checkpoint.description());
        System.out.print("Your answer: ");

        var answer = new Scanner(System.in).nextLine();

        mgr.respond(checkpoint.id(), HitlResponse.approve(answer));
    }
});

try (var agentican = Agentican.builder()
        .config(config)
        .hitlManager(hitlManager)
        .build()) {

    agentican.run("Plan a team offsite. Ask me anything you need to know.").result();
}
```

The agent's `ASK_QUESTION` tool result will be `{"question": "...", "answer": "..."}`, so it can use the answer in subsequent reasoning.

## Loop Over Items

Process a list in parallel by composing a producer step with a loop:

```java
var task = Plan.builder("create-report-cards")
        .step("get-students", "data-fetcher",
                "Fetch all students as a JSON array with name and grades")
        .loop("create-cards", loop -> loop
                .over("get-students")
                .step(PlanStepAgent.builder("create-card")
                        .agent("report-writer")
                        .instructions("Create a report card for {{item.name}} with grades {{item.grades}}")
                        .toolkit("notion")
                        .build()))
        .build();

agentican.run(task).result();
```

The loop iterations run in parallel — each on its own virtual thread, with its own sub-task execution.

## Branch on Classification

Route work based on a classifier step's output:

```java
var task = Plan.builder("triage")
        .step("classify", "classifier",
                "Classify this email: {{param.email}}. Return one word: 'urgent', 'normal', or 'spam'")
        .branch("route", branch -> branch
                .from("classify")
                .path("urgent",
                        PlanStepAgent.builder("alert").agent("notifier")
                                .instructions("Alert the team about an urgent email").build())
                .path("normal",
                        PlanStepAgent.builder("queue").agent("queue-manager")
                                .instructions("Add to normal queue").build())
                .path("spam",
                        PlanStepAgent.builder("discard").agent("logger")
                                .instructions("Log and discard").build())
                .defaultPath("normal"))
        .build();
```

## Typed Code Step (Deterministic Java in a Plan)

When a step is deterministic — an HTTP call, a database lookup, a computed enrichment — there's no need to round-trip through an LLM. Register a `CodeStep<I, O>` with typed input and output records; the framework wires Jackson at the boundaries so the executor stays in plain Java.

```java
record HttpInput(String url, String method) {
    public HttpInput { if (method == null) method = "GET"; }
}
record HttpOutput(String body, int status) { }

var agentican = Agentican.builder()
        .config(config)
        .codeStep(
                CodeStepSpec.of("http-get", HttpInput.class, HttpOutput.class),
                (HttpInput input, StepContext ctx) -> {
                    var response = HttpClient.newHttpClient().send(
                            HttpRequest.newBuilder(URI.create(input.url()))
                                    .method(input.method(), HttpRequest.BodyPublishers.noBody())
                                    .build(),
                            HttpResponse.BodyHandlers.ofString());
                    return new HttpOutput(response.body(), response.statusCode());
                })
        .plan(PlanConfig.builder()
                .name("payment-enrichment").externalId("payment-enrichment")
                .param("customer_id", "Customer to enrich", null, true)
                .codeStep("fetch-customer", s -> s
                        .code("http-get")
                        .input(new HttpInput(
                                "https://api.internal/customers/{{param.customer_id}}",
                                "GET")))
                .step("decide", s -> s
                        .agent("Risk Analyst")
                        .instructions("Customer record:\n{{step.fetch-customer.output.body}}\n\n"
                                    + "HTTP status was {{step.fetch-customer.output.status}}.")
                        .dependencies("fetch-customer"))
                .build())
        .build();
```

The agent reads individual fields from the typed JSON output via `{{step.X.output.field}}`, not the raw blob. See [Plans & Steps → PlanStepCode](tasks.md#planstepcodei) for the full contract.

## Multiple LLMs (Cost Optimization)

Use a fast/cheap model for classification and a stronger one for content generation:

```java
var config = RuntimeConfig.builder()
        .llm(LlmConfig.builder().name("default").apiKey(key).model("claude-sonnet-4-5").build())
        .llm(LlmConfig.builder().name("haiku").apiKey(key).model("claude-haiku-4-5").build())
        .agent(AgentConfig.forCatalog("agent.classifier.v1", "classifier", "Quick classifier", "haiku"))
        .agent(AgentConfig.forCatalog("agent.writer.v1",     "writer",     "High-quality writer", "default"))
        .build();
```

## Querying Task Logs

After a task runs, the `TaskLog` contains the full execution history:

```java
var taskStateStore = new MemTaskStateStore();

try (var agentican = Agentican.builder()
        .config(config)
        .taskStateStore(taskStateStore)
        .build()) {

    agentican.run("Do some work").result();

    // Inspect logs
    for (var log : taskStateStore.list()) {

        System.out.println("Task: " + log.taskName() + " (" + log.status() + ")");
        System.out.println("  Tokens: " + log.inputTokens() + " in / " + log.outputTokens() + " out");

        for (var step : log.steps().values()) {

            System.out.println("  Step: " + step.stepName() + " (" + step.status() + ")");
            System.out.println("    Runs: " + step.runCount());
        }
    }
}
```

## Custom LLM Client

LLM retry is built in — every client is automatically wrapped with `RetryingLlmClient` (exponential backoff with jitter). For custom providers or additional wrapping (logging, caching), inject your own `LlmClient`:

```java
LlmClient myClient = request -> {
    // your custom LLM provider
};

Agentican.builder()
        .config(config)
        .llm("default", myClient)  // still gets automatic retry wrapping
        .build();
```

## Cancellation

Cancel a long-running task from another thread:

```java
var handle = agentican.run("Long-running analysis...");

// On another thread (e.g., in response to a user clicking Cancel)
handle.cancel();

// The result will be CANCELLED
var result = handle.result();
```

## Next Steps

- [Getting Started](getting-started.md) — installation and basics
- [Core Concepts](concepts.md) — architecture
- [API Reference](https://github.com/your-org/agentican/javadoc/) — javadoc
