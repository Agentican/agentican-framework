# Agent Knowledge

Agent knowledge is a persistent, structured memory store that agents can recall during task execution. Unlike the per-task `ScratchpadToolkit`, knowledge entries survive across tasks and are shared across all agents.

## Concepts

### KnowledgeEntry

A topical container with extracted facts:

```
KnowledgeEntry
  ├── id, name, description
  ├── status: INDEXING | INDEXED | FAILED
  ├── facts: List<KnowledgeFact>         ← extracted atomic facts
  └── created, updated
```

Only entries with status `INDEXED` appear in the agent's knowledge index. Use `INDEXING` while you're populating an entry, then flip to `INDEXED` when ready.

Constructors:

```java
// New entry (defaults status=INDEXING, timestamps=now)
new KnowledgeEntry(String id, String name, String description);
KnowledgeEntry.of(String name, String description);   // generates UUID

// Round-trip constructor for persistent stores — rehydrates without stamping fresh timestamps
new KnowledgeEntry(String id, String name, String description,
                   KnowledgeStatus status, Instant created, Instant updated);
```

### KnowledgeFact

An atomic, taggable piece of knowledge:

```java
record KnowledgeFact(
    String id,
    String name,
    String content,
    List<String> tags,    // supports hierarchical "domain/topic/sub"
    Instant created,
    Instant updated
)
```

```java
KnowledgeFact.of(name, content, tags);   // generates UUID + timestamps
```

The framework doesn't extract facts from binary files (PDFs, Office docs, images) — that's app-specific. Extract facts yourself and call `entry.addFact(...)`, or implement your own `KnowledgeExtractor` that handles your formats.

## Setting Up Knowledge

Provide a `KnowledgeStore` to the Agentican builder. If you don't, a `KnowledgeStoreMemory` is created by default.

```java
import ai.agentican.framework.knowledge.*;
import ai.agentican.framework.store.KnowledgeStoreMemory;

var knowledgeStore = new KnowledgeStoreMemory();

try (var agentican = Agentican.builder()
        .knowledgeStore(knowledgeStore)
        .build()) {

    // ... use the framework
}
```

For production, implement `KnowledgeStore` against your database:

```java
public class DatabaseKnowledgeStore implements KnowledgeStore {

    @Override public void save(KnowledgeEntry entry) { /* persist */ }
    @Override public KnowledgeEntry get(String id) { /* fetch */ }
    @Override public List<KnowledgeEntry> all() { /* list */ }
    @Override public List<KnowledgeEntry> indexed() { /* status == INDEXED */ }
    @Override public void delete(String id) { /* delete */ }
}
```

## Adding Knowledge Manually

Create entries and populate them with facts:

```java
var entry = KnowledgeEntry.of("Q1 Pricing", "Pricing decisions for Q1 2026");

entry.addFact(KnowledgeFact.of(
    "Pro plan price",
    "Pro tier is $49/month effective Jan 1, 2026.",
    List.of("pricing/saas", "decision/q1")));

entry.addFact(KnowledgeFact.of(
    "Free trial length",
    "All new signups get a 14-day free trial.",
    List.of("pricing/trial", "policy")));

entry.setStatus(KnowledgeStatus.INDEXED);
knowledgeStore.save(entry);
```

Once `INDEXED`, the entry appears in the agent's knowledge index automatically.

## Automatic Extraction

When a `KnowledgeStore` is configured, the framework wires a `KnowledgeIngestor` as an `AgenticanEventListener` subscribed to the bus. On every `StepCompleted` event, if the step's output contains the marker string `KNOWLEDGE_ACQUIRED`, the ingestor:

1. Strips the marker from the output
2. Calls a `KnowledgeExtractor` (default: `LlmKnowledgeExtractor`) with `(step input, step output, existing indexed entries)`
3. Applies each extracted entry as `CREATE` (new entry) or `UPDATE` (merge facts into an existing entry)
4. Saves the resulting entries to the store with status `INDEXED`

The actual extractor work runs asynchronously on the task executor — it does not block the agent loop or the bus's publishing thread.

The agent opts in by including `KNOWLEDGE_ACQUIRED` in its final step output when it has learned something worth retaining. Your agent's `role` / system prompt should instruct it when to emit the marker.

### How "step input" is resolved

The extractor takes the original user task as its `input` argument (so it can correlate the question that was asked with the facts being learned). The ingestor learns that input by subscribing to `RunStarted`, `TurnStarted`, and `MessageSent` events — it tracks `runId → stepId`, `turnId → stepId`, and remembers the first `MessageSent.request().userTask()` per step. On `StepCompleted` it pops the remembered task and feeds it to the extractor. No store reads — the input is derived purely from event payloads.

Cross-state cleanup happens on `TaskCompleted`; in-progress tasks that get reaped have their step-state evicted at completion time.

**Eviction caveat**: the maps are keyed by `runId` / `turnId` / `stepId`, not by `taskId`. Steps that get abandoned mid-flight without a `StepCompleted` (e.g. a workflow execution thread killed by JVM signal before persistence) leave orphan entries until the surrounding `TaskCompleted` fires — and if neither fires (worst case: the entire process is `kill -9`ed), the in-memory state is gone with the process anyway. In practice the tables are small (one entry per active step / run / turn) and short-lived per task, but very long-lived servers handling many abandoned tasks would benefit from a precise eviction path. A future enrichment could add `taskId` to `MessageSent` / `TurnStarted` events to make eviction exact.

### Works under Temporal too

Because Temporal-driven workflows route their events through the same `Agentican.eventBus()` as in-process work (see [Temporal Integration](temporal.md#event-flow-under-temporal)), `KnowledgeIngestor` automatically processes step outputs from Temporal-managed agents without any extra wiring. The same `KNOWLEDGE_ACQUIRED` marker, the same extractor, the same store.

### Batch reingestion

For one-shot reingestion outside the event-driven path — e.g. on startup, replaying knowledge from completed steps that fired before the ingestor was wired — call `KnowledgeIngestor.ingestStep(stepName, input, output)` directly. The framework's `AgenticanRecovery` uses this when resuming interrupted tasks; it reads the persisted first-turn user task from `WorkflowRunStore` and hands it to `ingestStep`. Store reads are fine in batch contexts; the event-driven path stays pure.

`LlmKnowledgeExtractor` uses the framework's default LLM (whichever is registered under `LlmConfig.DEFAULT`). Supply your own `KnowledgeExtractor` implementation to customize extraction:

```java
public interface KnowledgeExtractor {

    List<ExtractedEntry> extract(
            String input,
            String output,
            List<KnowledgeEntry> existingEntries);
}
```

## How Agents Use Knowledge

When a `KnowledgeStore` is provided:

1. **Index in user message** — every agent turn renders a `<knowledge-base><index>` section listing all `INDEXED` entries (id, name, description) so the agent knows what's available.

2. **Recall tool** — the framework registers a `RECALL_KNOWLEDGE` tool the agent can call:

   ```
   RECALL_KNOWLEDGE(entry_ids: ["abc-123", "def-456"])
     → returns full entries with facts and tags
   ```

3. **Recalled section** — once the agent recalls entries, they appear in subsequent turns under `<knowledge-base><recalled>` with full facts. The agent can use them in its reasoning without re-recalling.

The agent decides what's relevant. The system prompt tells it: scan the index, recall what looks useful, and only do fresh research if the topic isn't covered.

## Recall Flow Example

```
User task: "Plan our Q2 pricing strategy"
  ↓
Agent receives task + knowledge index showing:
  - "Q1 Pricing" — Pricing decisions for Q1 2026
  - "Customer Feedback" — Survey results from December
  ↓
Agent calls: RECALL_KNOWLEDGE(["q1-pricing-id", "feedback-id"])
  ↓
Agent receives full facts:
  - "Pro plan price: $49/month..."
  - "Free trial: 14 days..."
  - "78% of users want enterprise tier..."
  ↓
Agent reasons with the facts and produces the strategy
```

The agent never re-researches what's already in the knowledge base.

## Differences vs Scratchpad

| Feature | Scratchpad | Knowledge |
|---------|-----------|-----------|
| Lifetime | Per-task (ephemeral) | Persistent |
| Scope | Single task (shared by agents) | Shared across agents/tasks |
| Structure | Key/value | Entries → facts → tags |
| Indexed | No | Yes (visible in every prompt) |
| Recall | Direct key lookup | `RECALL_KNOWLEDGE` tool with ids |
| Persistence | In-memory only | Pluggable `KnowledgeStore` |

Both are available simultaneously. Use scratchpad for working memory within a task and knowledge for facts you want to retain across tasks.

## API Reference

### KnowledgeStore

```java
void save(KnowledgeEntry entry);
KnowledgeEntry get(String id);
List<KnowledgeEntry> all();
List<KnowledgeEntry> indexed();      // status == INDEXED
void delete(String id);
```

### KnowledgeEntry

```java
new KnowledgeEntry(String id, String name, String description);
new KnowledgeEntry(String id, String name, String description,
                   KnowledgeStatus status, Instant created, Instant updated);
KnowledgeEntry.of(String name, String description);

entry.addFact(KnowledgeFact);
entry.setStatus(KnowledgeStatus);
entry.setName(String);
entry.setDescription(String);
entry.clearFacts();
```

### KnowledgeFact

```java
record KnowledgeFact(String id, String name, String content,
                     List<String> tags, Instant created, Instant updated);

KnowledgeFact.of(String name, String content, List<String> tags);
```

### KnowledgeExtractor

```java
public interface KnowledgeExtractor {

    List<ExtractedEntry> extract(
            String input,
            String output,
            List<KnowledgeEntry> existingEntries);
}
```

`ExtractedEntry` carries a `CREATE` / `UPDATE` operation plus the target entry. The ingestor applies each one against the store.

### LlmKnowledgeExtractor

```java
new LlmKnowledgeExtractor(LlmClient llm);
```

## Next Steps

- [Tools & Toolkits](tools.md) — how `KnowledgeToolkit` fits into the toolkit model
- [Concepts](concepts.md) — overall architecture
- [Examples](examples.md) — recipes including knowledge usage
