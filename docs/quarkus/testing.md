# Testing

The `agentican-quarkus-test` module provides shared test utilities for writing `@QuarkusTest`
tests across all Agentican modules.

## Setup

```xml
<dependency>
    <groupId>ai.agentican</groupId>
    <artifactId>agentican-quarkus-test</artifactId>
    <version>${project.version}</version>
    <scope>test</scope>
</dependency>
```

Add the index dependency so Quarkus discovers the test beans:

```properties
# src/test/resources/application.properties
quarkus.index-dependency.agentican-test.group-id=ai.agentican
quarkus.index-dependency.agentican-test.artifact-id=agentican-quarkus-test
```

## MockLlmClient

`@Singleton @Named("default")` bean that replaces the real Anthropic client. Returns
canned responses from a FIFO queue. If no response is queued, returns a generic end-turn.

```java
@QuarkusTest
class MyAgentTest {

    @Inject MockLlmClient mockLlm;
    @Inject Agentican agentican;

    @BeforeEach
    void reset() { mockLlm.reset(); }

    @Test
    void taskCompletes() {
        // Queue the LLM response the agent will receive
        mockLlm.queueEndTurn("Here is the research result");

        var task = TestTaskBuilder.singleStep("test", "researcher", "Find papers");
        var result = agentican.run(task).result();

        assertEquals("COMPLETED", result.status().name());
    }
}
```

### Methods

| Method | Description |
|---|---|
| `queueEndTurn(String text)` | Queue a response that ends the turn |
| `queueToolCall(String id, String tool, Map args)` | Queue a tool-use response |
| `reset()` | Clear all queued responses |

## TestTaskBuilder

Convenience factory for common test task shapes:

```java
// Single agent step
var task = TestTaskBuilder.singleStep("my-task", "researcher", "Find papers");

// Two sequential steps (step2 depends on step1)
var task = TestTaskBuilder.twoStepSequential("my-task", "researcher",
    "Find papers", "Summarize findings");
```

## Testing patterns

### End-to-end via REST

```java
@QuarkusTest
class EndToEndTest {

    @Inject MockLlmClient mockLlm;
    @Inject ObjectMapper mapper;

    @Test
    void submitAndVerify() throws Exception {
        mockLlm.queueEndTurn("Result");

        var step = TaskStepAgent.of("s", "researcher", "do it",
            List.of(), false, List.of(), List.of());
        var task = Task.of("test", "d", List.of(), List.of(step));

        var taskId = given()
            .contentType("application/json")
            .body("{\"task\": " + mapper.writeValueAsString(task) + "}")
            .when().post("/agentican/tasks")
            .then().statusCode(201)
            .extract().jsonPath().getString("taskId");

        await().atMost(10, SECONDS).untilAsserted(() ->
            given().get("/agentican/tasks/" + taskId)
                .then().body("status", equalTo("COMPLETED")));
    }
}
```

### Testing CDI event observers

Use a `@Singleton` observer bean (not `@ApplicationScoped` — Quarkus test indexing quirk):

```java
@Singleton
public class EventCollector {
    public final List<TaskCompletedEvent> events = new ArrayList<>();
    public void onCompleted(@Observes TaskCompletedEvent e) { events.add(e); }
    public void reset() { events.clear(); }
}
```

### Testing HITL flow

Create a checkpoint via `HitlManager`, respond via REST:

```java
@Inject HitlManager hitlManager;

@Test
void hitlBridge() throws Exception {
    var toolCall = new ToolCall("c1", "send_email", Map.of("to", "user@test.com"));
    var checkpoint = hitlManager.createToolApprovalCheckpoint(toolCall, "step");

    // Park a virtual thread waiting for the response
    var future = CompletableFuture.supplyAsync(
        () -> hitlManager.awaitResponse(checkpoint.id()),
        Executors.newVirtualThreadPerTaskExecutor());

    // Respond via REST
    given().contentType("application/json")
        .body("{\"approved\": true}")
        .post("/agentican/checkpoints/" + checkpoint.id() + "/respond")
        .then().statusCode(204);

    // The parked thread wakes up
    var response = future.get(5, SECONDS);
    assertTrue(response.approved());
}
```

## Tips

- Use `@Singleton` (not `@ApplicationScoped`) for test observer beans — `@ApplicationScoped`
  proxies don't always register `@Observes` methods in test sources
- Always call `mockLlm.reset()` in `@BeforeEach` to avoid cross-test state leakage
- For SSE tests, use `java.net.http.HttpClient` with line-by-line parsing
- For WebSocket tests, use `java.net.http.WebSocket` (standard Java API)
