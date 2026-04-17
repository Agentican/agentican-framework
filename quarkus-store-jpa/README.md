# Agentican Quarkus Store (JPA)

JPA-backed implementations of Agentican's framework stores: `TaskStateStore`,
`KnowledgeStore`, `AgentRegistry`, `SkillRegistry`, `PlanRegistry`. Postgres in
production, H2 in tests. Flyway ships the schema.

Drop the jar on the classpath and agents, skills, plans, task execution state,
and knowledge entries persist across restarts. The in-memory fallbacks from
`agentican-quarkus-runtime` are superseded automatically.

## Setup

```xml
<dependency>
    <groupId>ai.agentican</groupId>
    <artifactId>agentican-quarkus-store-jpa</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```

Add the index entry so Quarkus scans the beans:

```properties
quarkus.index-dependency.agentican-store-jpa.group-id=ai.agentican
quarkus.index-dependency.agentican-store-jpa.artifact-id=agentican-quarkus-store-jpa
```

Configure a datasource. With Dev Services this is zero-config:

```properties
quarkus.datasource.db-kind=postgresql
quarkus.datasource.devservices.enabled=true
quarkus.datasource.devservices.reuse=true

quarkus.hibernate-orm.database.generation=none
quarkus.flyway.migrate-at-start=true
quarkus.flyway.locations=classpath:db/migration
```

> **Data survives restarts only** if `testcontainers.reuse.enable=true` is set
> in `~/.testcontainers.properties`. Without it Testcontainers tears down the
> container (and all data) when the JVM exits, even with `devservices.reuse=true`.

See `agentican-server/src/main/resources/application.properties` for a working
reference wiring.

## What it provides

| Bean | Implementation | Replaces |
|---|---|---|
| `TaskStateStore` | `JpaTaskStateStore` | `MemTaskStateStore` |
| `KnowledgeStore` | `JpaKnowledgeStore` | `MemKnowledgeStore` |
| `AgentRegistry` | `JpaAgentRegistry` | `InMemoryAgentRegistry` |
| `SkillRegistry` | `JpaSkillRegistry` | `InMemorySkillRegistry` |
| `PlanRegistry` | `JpaPlanRegistry` | `InMemoryPlanRegistry` |

All beans are gated with:

```java
@IfBuildProperty(name = "agentican.store.backend",
                 stringValue = "jpa",
                 enableIfMissing = true)
```

So they activate automatically when the module is on the classpath. Opt back
out with:

```properties
agentican.store.backend=memory
```

## External IDs (required)

Agents and skills declared in `application.properties` **must** carry a stable
`external-id`. It's the bridge between config-declared entities and their
persisted rows across deploys.

```properties
agentican.agents[0].name=researcher
agentican.agents[0].role=Expert at finding information
agentican.agents[0].external-id=researcher

agentican.skills[0].name=web-search
agentican.skills[0].instructions=Search the web with the provided tools
agentican.skills[0].external-id=web-search
```

If an agent or skill is declared without `external-id`, Agentican throws an
`IllegalStateException` at boot:

```
IllegalStateException: agent 'researcher' is missing an externalId.
```

On first boot the registry inserts a row with a generated id. On subsequent
boots the row is located by `external_id` and the config-declared name/role/llm
is written back — so you can edit config freely without churning primary keys.

Planner-created plans, agents, and skills have a `null` external id: they're
reused only within the same process via the in-memory index inside each
registry.

## Schema

Flyway migration `V1__init.sql` creates:

- **Catalog** — `agents`, `skills`, `tools` (reusable across plans)
- **Plans** — `plans` (top-level row plus full `definition_json` serialization)
- **Task execution** — `tasks`, `task_steps`, `runs`, `turns`, `tool_results`
- **Knowledge** — `knowledge_entries`, `knowledge_facts`

Tasks carry a `plan_snapshot_json` so the plan shape at dispatch time survives
even if the plan row is later overwritten.

## Planner reuse

Because plans live in Postgres keyed by `external_id`, the `PlannerAgent` can
reuse persisted plans across restarts. The first run materializes the plan;
subsequent runs with matching `external_id` load the prior definition instead
of re-planning.

## Resume after restart

When persistence is enabled, any task left RUNNING when the server dies is automatically resumed on next startup. No user action required.

Behavior:
- Completed steps are **skipped**; their outputs are reused from the log.
- An in-flight agent step is resumed at **turn-boundary granularity**:
  - If the LLM call hadn't returned, the turn is marked `ABANDONED` and re-issued.
  - If the response arrived but tools hadn't all run, the response is **replayed in-process** (no second LLM call) and only the *missing* tool calls execute. Completed tool calls are not re-run.
- An in-flight loop step resumes child-by-child: completed iterations keep their outputs, running iterations are recursively resumed, missing iterations dispatch. The `UNIQUE (parent_task_id, iteration_index)` constraint prevents duplicates.
- An in-flight branch step skips classifier re-evaluation — the chosen path is persisted in `task_steps.branch_chosen_path` and used verbatim.
- Pending HITL checkpoints are rehydrated so the UI's Approve/Reject buttons still work after restart.
- Completed steps re-fire `KnowledgeIngestor` in case extraction didn't finish pre-crash (the extractor dedupes, so this is safe to run).
- Spans emitted during resume carry `agentican.resumed=true` so the trace waterfall shows which spans came from post-restart execution.

Tasks that can't be resumed (missing plan, status already terminal) are reaped — marked FAILED with a structured cause so the UI's Recent Tasks panel doesn't show zombie RUNNING rows.

Controlled by `agentican.resume-on-start` (default `true`).

### Plan snapshot preference (stale-plan semantics)

On resume, the task is rehydrated from `tasks.plan_snapshot_json` — the plan shape
captured at dispatch time — **not** from the current plan in the registry. This is
deliberate: the shape of the plan is part of the task's durable state, and swapping
shapes mid-flight produces non-deterministic behavior (step names referenced in
completed outputs may no longer exist, branch paths may have moved, etc.).

Consequence: if you intentionally edit a registered plan expecting in-flight tasks
to pick up the edit, **they won't**. The snapshot wins. New tasks started after the
edit use the updated plan; interrupted tasks finish on the shape they started with.
If the snapshot blob is corrupt and can't be deserialized, the classifier reaps
the task with `reason=PLAN_CORRUPT` rather than silently falling back to the
registry.

### Known at-least-once window

One tool call can run twice: the one that was in flight at the exact moment of crash. We cannot distinguish "tool was about to run" from "tool ran but its completion wasn't persisted." Tool authors whose side effects are non-idempotent (Slack messages, Notion page creation, payment APIs) should design for at-most-once invocation per crash, or accept occasional duplicates. Everything else — LLM calls, completed tools, completed steps — is deterministically skipped.

## Testing

The module ships H2 for tests. A test `application.properties`:

```properties
quarkus.datasource.db-kind=h2
quarkus.datasource.jdbc.url=jdbc:h2:mem:test
quarkus.hibernate-orm.database.generation=none
quarkus.flyway.migrate-at-start=true
quarkus.flyway.locations=classpath:db/migration
```

## What's not in MVP

- Retention / pruning — rows accumulate forever. Add a scheduled cleanup job if
  you need it.
- Cross-registry analytics ("which plans use agent X") — plan bodies are stored
  as JSON, not decomposed into relations.
- Multi-tenancy — single-tenant schema.
