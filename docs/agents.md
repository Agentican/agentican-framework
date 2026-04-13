# Agents

An `Agent` represents a specialized worker — a name, role, optional skills, and a runner that executes the actual work.

## Defining Agents

The simplest way to define an agent is via configuration:

```java
var researcher = AgentConfig.builder()
        .name("researcher")
        .role("Expert researcher who finds and synthesizes information")
        .skill(SkillConfig.of("citations", "Always include source URLs"))
        .build();

var config = RuntimeConfig.builder()
        .llm(LlmConfig.builder().apiKey(apiKey).build())
        .agent(researcher)
        .build();
```

Agents from config are pre-registered in the `AgentRegistry` when Agentican starts. They're available for tasks to reference by name.

## Agents from Planning

You usually don't need to pre-define agents. The planner creates them on the fly based on the task description:

```java
agentican.run("Find the top 3 LLMs and create a report comparing them").result();
```

The planner might create:
- **`AI Research Specialist`** — handles the research step
- **`Documentation Specialist`** — writes the report

These get built and registered automatically. Subsequent tasks can reference the same agents (the planner uses existing ones when their roles match).

## Agent Anatomy

```java
record Agent(
    String name,
    String role,
    List<SkillConfig> skills,
    AgentRunner runner
)
```

- **`name`** — unique identifier, used in task steps
- **`role`** — short description of the agent's expertise (used in the system prompt)
- **`skills`** — optional named instruction sets that activate per-step
- **`runner`** — the execution strategy (almost always `SmacAgentRunner`)

## Skills

Skills are reusable instruction blocks. Define them once on the agent, activate them per-step.

```java
var analyst = AgentConfig.builder()
        .name("analyst")
        .role("Data analyst")
        .skill(SkillConfig.of("statistical-rigor",
                "Use p-values, confidence intervals, and explain assumptions"))
        .skill(SkillConfig.of("plain-english",
                "Translate findings into non-technical language"))
        .build();
```

A task step then enables the relevant skills:

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

The default runner. Configurable via `Agentican` builder through `WorkerConfig`:

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

When the planner creates multiple agents, they don't talk to each other directly. Instead, they share state through the task graph:

- Agent A produces output for step `research`
- Agent B's step `summarize` depends on `research`, with instructions like `Summarize: {{step.research.output}}`
- The runner resolves the placeholder and gives Agent B the output as data

This keeps each agent stateless and independent. The task graph is the integration layer.

## LLM Selection

By default, all agents use the `default` LLM client. You can specify a different one per agent:

```java
AgentConfig.builder()
        .name("fast-classifier")
        .role("Quick yes/no classifier")
        .llm("haiku")  // ← LLM name from RuntimeConfig
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
