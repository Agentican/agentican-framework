# Agents

An `Agent` represents a specialized worker — a name, role, an `AgentRunner` that executes the actual work, and the `AgentConfig` it was built from.

## Defining Agents

The simplest way to define an agent is via configuration:

```java
var researcher = AgentConfig.forCatalog(
        "agent.researcher.v1",                         // externalId — required for catalog agents
        "researcher",                                  // name
        "Expert researcher who finds and synthesizes information",
        "default");                                    // llm name

var config = RuntimeConfig.builder()
        .llm(LlmConfig.builder().apiKey(apiKey).build())
        .agent(researcher)
        .build();
```

Any agent you register at startup — through `RuntimeConfig.agents` or `Agentican.builder().agent(...)` — must declare an `externalId`. Planner-created agents don't need one.

Agents from config are pre-registered in the `AgentRegistry` when Agentican starts. They're available for plan steps to reference by name or id.

## Agents from Planning

You usually don't need to pre-define agents. The planner creates them on the fly based on the task description:

```java
agentican.run("Find the top 3 LLMs and create a report comparing them").result();
```

The planner might create:
- **`AI Research Specialist`** — handles the research step
- **`Documentation Specialist`** — writes the report

Any planner-introduced `AgentConfig` that isn't already in the registry is passed through `AgentFactory` and registered. Subsequent tasks can reference the same agents (the planner sees them in its prompt).

## Agent Anatomy

```java
record Agent(
    String id,
    String name,
    String role,
    AgentRunner runner,
    AgentConfig config   // null for Agent.of(name, role, runner) — typically only in tests
)
```

- **`id`** — internal UUID (auto-generated if null)
- **`name`** — display name, used in plan step references
- **`role`** — short description of the agent's expertise (used in the system prompt)
- **`runner`** — the execution strategy (almost always `SmacAgentRunner`)
- **`config`** — the `AgentConfig` this agent was built from; `null` if built via the legacy `Agent.of(name, role, runner)` / `Agent.of(id, name, role, runner)` factories (used in test fixtures — these agents do not persist to a catalog)

Preferred factory:

```java
Agent.of(agentConfig, runner);    // preserves config on the Agent record
```

## AgentFactory

`AgentFactory` turns an `AgentConfig` into a runtime `Agent`. It's wired with the LLM clients, the HITL manager, the knowledge store, the task state store, the skill registry, and the task listener:

```java
var factory = new AgentFactory(
        config, llms, hitlManager, knowledgeStore,
        taskStateStore, skillRegistry, taskListener);

Agent agent = factory.build(agentConfig);
```

`Agentican` constructs the factory internally. Persistent agent registries can call it from their `seed(factory)` hook to rehydrate cataloged agents at boot.

## Skills

Skills are reusable instruction blocks. They live in a top-level `SkillRegistry` (seeded from `RuntimeConfig.skills` and the fluent builder) and are referenced by plan steps.

```java
var config = RuntimeConfig.builder()
        .llm(...)
        .skill(SkillConfig.forCatalog("skill.statistical-rigor.v1", "statistical-rigor",
                "Use p-values, confidence intervals, and explain assumptions"))
        .skill(SkillConfig.forCatalog("skill.plain-english.v1", "plain-english",
                "Translate findings into non-technical language"))
        .agent(AgentConfig.forCatalog("agent.analyst.v1", "analyst", "Data analyst", "default"))
        .build();
```

A plan step activates skills by name or id:

```java
PlanStepAgent.builder("explain-findings")
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
                    String stepId, String stepName);

    default AgentResult resume(Agent agent, String task, List<String> activeSkills,
                               RunLog savedRun, List<ToolResult> hitlToolResults,
                               Map<String, Toolkit> toolkits, String taskId,
                               String stepId, String stepName) {
        throw new UnsupportedOperationException("This runner does not support HITL resume");
    }
}
```

The default `SmacAgentRunner` handles:
- Multi-turn LLM conversations
- Parallel tool execution
- HITL suspension on approval/question tools
- Resumption with HITL responses
- Timeouts and max-turn limits
- Scratchpad memory across turns

For most cases, you don't need a custom runner. If you build one, the framework will use it via the agent's `runner` field. HITL resume is optional — only implement it if your runner supports suspension.

## SmacAgentRunner

The default runner. Configurable via `WorkerConfig`:

```java
var config = RuntimeConfig.builder()
        .llm(...)
        .worker(WorkerConfig.builder()
                .maxTurns(20)                    // max LLM turns per step
                .timeout(Duration.ofMinutes(10)) // per-step timeout
                .build())
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
        .externalId("agent.fast-classifier.v1")
        .name("fast-classifier")
        .role("Quick yes/no classifier")
        .llm("haiku")                 // ← LLM name from RuntimeConfig
        .build()
```

Define the LLMs in config:

```java
RuntimeConfig.builder()
        .llm(LlmConfig.builder().name("default").apiKey(key).model("claude-sonnet-4-5").build())
        .llm(LlmConfig.builder().name("haiku").apiKey(key).model("claude-haiku-4-5").build())
        .build();
```

Or supply pre-built `LlmClient` instances via the builder:

```java
Agentican.builder()
        .config(config)
        .llm("custom", myLlmClient)
        .build();
```

## Next Steps

- [Tasks & Steps](tasks.md) — how agents fit into workflows
- [Tools & Toolkits](tools.md) — give agents tools to use
- [Configuration](configuration.md) — full config reference
