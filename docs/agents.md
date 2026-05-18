# Agents

An `Agent` represents a specialized worker — a name, role, an `AgentRunner` that executes the actual work, and the `AgentConfig` it was built from.

## Defining Agents

The simplest way to define an agent is via configuration:

```java
var researcher = AgentConfig.builder()
        .id("researcher")
        .name("researcher")
        .role("Expert researcher who finds and synthesizes information")
        .llm("default")
        .build();

try (var agentican = Agentican.builder()
        .configuration().api()
            .llm().apiKey(apiKey).end()
            .end()
        .registry().api()
            .agent(researcher)
            .end()
        .build()) {
    // use agentican
}
```

Agents from config are pre-registered in the `AgentRegistry` when Agentican starts. Workflow steps reference them by **name** via `WorkflowStepAgent.agentName`.

## Agents from Planning

You usually don't need to pre-define agents. The planner creates them on the fly based on the task description:

```java
String text = agentican.run("Find the top 3 LLMs and create a report comparing them").await();
```

The planner might create:
- **`AI Research Specialist`** — handles the research step
- **`Documentation Specialist`** — writes the report

Any planner-introduced `AgentConfig` that isn't already in the registry is passed through `AgentFactory` and registered. Subsequent tasks can reference the same agents (the planner sees them in its prompt).

## Agent Anatomy

```java
record Agent(
    AgentConfig config,
    AgentRunner runner
)
```

- **`config`** — the `AgentConfig` carrying id (required), name, role, and LLM choice. The id is the stable identifier the registry uses; the planner picks slug-style ids for agents it manufactures.
- **`runner`** — the execution strategy (almost always `SmacAgentRunner`). Required.

`Agent` exposes `id()`, `name()`, and `role()` as delegating accessors that read from `config`.

Construction is builder-only:

```java
Agent.builder().config(agentConfig).runner(runner).build();
```

## AgentFactory

`AgentFactory` turns an `AgentConfig` into a runtime `Agent`. It's wired with the LLM clients, the HITL manager, the knowledge store, the task state store, the skill registry, and the task listener:

```java
var factory = AgentFactory.builder()
        .config(runtimeConfig)
        .llms(llms)
        .hitlManager(hitlManager)
        .knowledgeStore(knowledgeStore)
        .workflowRunStore(taskStateStore)
        .skillRegistry(skillRegistry)
        .taskListener(taskListener)
        .build();

Agent agent = factory.build(agentConfig);
```

`Agentican` constructs the factory internally. Persistent agent registries can call it from their `seed(factory)` hook to rehydrate cataloged agents at boot.

## Skills

Skills are reusable instruction blocks. They live in a top-level `SkillRegistry` (seeded from `CatalogConfig.skills` and the fluent builder) and are referenced by workflow steps.

```java
Agentican.builder()
        .configuration().api()
            .llm(...)
            .end()
        .registry().api()
            .skill()
                    .id("statistical-rigor")
                    .name("statistical-rigor")
                    .instructions("Use p-values, confidence intervals, and explain assumptions").end()
            .skill()
                    .id("plain-english")
                    .name("plain-english")
                    .instructions("Translate findings into non-technical language").end()
            .agent()
                    .id("analyst")
                    .name("analyst")
                    .role("Data analyst").llm("default").end()
            .end()
        .build();
```

A workflow step activates skills by name:

```java
WorkflowStepAgent.builder("explain-findings")
        .agent("analyst")
        .instructions("Explain the results to a general audience")
        .skill("plain-english")  // ← only this skill is activated for this step
        .build();
```

The agent's system prompt for that step will include the `plain-english` instructions but not `statistical-rigor`.

## Custom AgentRunners

`AgentRunner` is an interface — you can plug in your own execution strategy if you need something different from the default loop:

```java
public interface AgentRunner {

    AgentResult run(Agent agent, String task, List<String> activeSkills,
                    Map<String, Toolkit> toolkits, String taskId,
                    String stepId, String stepName, Duration timeoutOverride);

    default AgentResult resume(Agent agent, String task, List<String> activeSkills,
                               RunLog savedRun, List<ToolResult> hitlToolResults,
                               Map<String, Toolkit> toolkits, String taskId,
                               String stepId, String stepName, Duration timeoutOverride) {
        throw new UnsupportedOperationException("This runner does not support HITL resume");
    }

    default AgentResult resumeAfterCrash(Agent agent, String task, List<String> activeSkills,
                                         RunLog savedRun, ResumePlan resumePlan,
                                         Map<String, Toolkit> toolkits, String taskId,
                                         String stepId, String stepName,
                                         AtomicBoolean cancelled) {
        return AgentResult.builder().status(AgentStatus.FAILED).run(savedRun).build();
    }
}
```

`timeoutOverride` lets a step override the runner's default timeout; pass `null` to use the runner's configured value. `resumeAfterCrash` is invoked by `AgenticanRecovery` for crash recovery and has a sensible default — only override it if your runner has special crash-recovery semantics.

The default `SmacAgentRunner` handles:
- Multi-turn LLM conversations
- Parallel tool execution
- HITL suspension on approval/question tools
- Resumption with HITL responses
- Timeouts and max-turn limits
- Scratchpad memory across turns

For most cases, you don't need a custom runner. If you build one, the framework will use it via the agent's `runner` field. HITL resume is optional — only implement it if your runner supports suspension.

### Alternative: `ReActAgentRunner`

The framework also ships a `ReActAgentRunner` — a leaner thought→action→observation loop, useful for agents whose system prompt expects the classic ReAct pattern (`Thought: ... Action: ... Observation: ...`). It supports the same core surface (multi-turn, parallel tool execution, timeouts, max-turn limits) but does not currently implement HITL suspension/resume. Construct one explicitly when building the agent:

```java
var runner = ReActAgentRunner.builder()
        .llmClient(myLlm)
        .maxIterations(20)
        .timeout(Duration.ofMinutes(10))
        .build();

Agent.builder().config(config).runner(runner).build();
```

If you're not sure which to use, stick with `SmacAgentRunner` — it's the default and supports the full feature set.

## SmacAgentRunner

The default runner. Configurable via `WorkerConfig`:

```java
Agentican.builder()
        .configuration().api()
            .llm().apiKey(apiKey).end()
            .worker()
                .maxTurns(20)                    // max LLM turns per step
                .timeout(Duration.ofMinutes(10)) // per-step timeout
                .end()
            .end()
        .build();
```

The runner does roughly this on each turn:

1. Render user message (task + scratchpad + tool results from previous turn)
2. Send LLM request with available tool definitions
3. If LLM returns text only → return `AgentResult(COMPLETED)`
4. Categorize tool calls (normal / approval / question)
5. Execute normal tools in parallel
6. If approval/question tools present → create checkpoint, return `AgentResult(SUSPENDED)`
7. Store results in scratchpad, increment turn, repeat

## Multi-Agent Collaboration

When the planner creates multiple agents, they don't talk to each other directly. Instead, they share state through the plan graph:

- Agent A produces output for step `research`
- Agent B's step `summarize` depends on `research`, with instructions like `Summarize: {{step.research.output}}`
- The runner resolves the placeholder and gives Agent B the output as data

This keeps each agent stateless and independent. The plan graph is the integration layer.

## LLM Selection

By default, all agents use the `default` LLM client. You can specify a different one per agent:

```java
AgentConfig.builder()
        .id("fast-classifier")
        .name("fast-classifier")
        .role("Quick yes/no classifier")
        .llm("haiku")                 // ← LLM name from EngineConfig
        .build()
```

Define the LLMs in config:

```java
Agentican.builder()
        .configuration().api()
            .llm().name("default").apiKey(key).model("claude-sonnet-4-5").end()
            .llm().name("haiku").apiKey(key).model("claude-haiku-4-5").end()
            .end()
        .build();
```

Or supply pre-built `LlmClient` instances via the builder:

```java
Agentican.builder()
        .llm("custom", myLlmClient)
        .build();
```

## Next Steps

- [Tasks & Steps](tasks.md) — how agents fit into workflows
- [Tools & Toolkits](tools.md) — give agents tools to use
- [Configuration](configuration.md) — full config reference
