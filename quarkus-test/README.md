# Agentican Quarkus Test

> Shared test fixtures for the Agentican Quarkus modules.

A test-scope utility jar that eliminates boilerplate across every Quarkus module's test suite. You get a deterministic LLM stand-in and a one-liner plan builder.

## Install

```xml
<dependency>
    <groupId>ai.agentican</groupId>
    <artifactId>agentican-quarkus-test</artifactId>
    <version>0.1.0-alpha.1</version>
    <scope>test</scope>
</dependency>
```

## `MockLlmClient`

A `@Singleton` CDI bean registered as `@Named("default")` — it **replaces** the real LLM client in tests. Queue responses in the order your code will consume them.

```java
@QuarkusTest
class MyTest {

    @Inject AgenticanRuntime agentican;
    @Inject MockLlmClient llm;

    @BeforeEach void reset() { llm.reset(); }

    @Test
    void plansAndRuns() {
        llm.queueEndTurn("done");
        var handle = agentican.run("say hello");
        assertThat(handle.result().status()).isEqualTo(TaskStatus.COMPLETED);
    }
}
```

| Method | Purpose |
|---|---|
| `queueEndTurn(String text)` | Queue a plain-text response that ends the turn. |
| `queueToolCall(String callId, String toolName, Map<String,Object> args)` | Queue a tool invocation. |
| `reset()` | Drain the queue. Call this in `@BeforeEach`. |

If the queue is empty, `send()` returns `"[mock: no queued response]"` with `END_TURN` — loud enough to notice in failing assertions without throwing.

## `TestTaskBuilder`

Two static helpers for building `Plan` objects in tests without wiring the full `PlanConfig` DSL.

```java
Plan single = TestTaskBuilder.singleStep("t1", "researcher", "find X");
Plan chain  = TestTaskBuilder.twoStepSequential("t2", "researcher", "find X", "summarize X");
```

Both produce minimal valid plans (no skills, no params, `enabled=true`) — good for wiring tests, not for exercising planner behavior.

## Related

- [`quarkus-integration-tests`](../quarkus-integration-tests/) — the biggest consumer.
- [Top-level module index](../README.md#modules).
