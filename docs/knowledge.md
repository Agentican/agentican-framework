# Agent Knowledge

Agent knowledge is a persistent, structured memory store that agents can recall during task execution. Unlike the per-task `ScratchpadToolkit`, knowledge entries survive across tasks and are shared across all agents.

## Concepts

### KnowledgeEntry

A topical container with extracted facts:

```
KnowledgeEntry
  ├── id, name, description
  ├── status: INDEXING | INDEXED | FAILED
  ├── files: List<KnowledgeFile>         ← optional source documents
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

### KnowledgeFile

Optional source document attached to an entry:

```
KnowledgeFile
  ├── id, name, type, size
  └── contents: byte[]
```

The framework doesn't extract facts from binary files (PDFs, Office docs, images) — that's app-specific. Extract facts yourself and call `entry.addFact(...)`, or implement your own `KnowledgeExtractor` that handles your formats.

## Setting Up Knowledge

Provide a `KnowledgeStore` to the Agentican builder. If you don't, a `MemKnowledgeStore` is created by default.

```java
import ai.agentican.framework.knowledge.*;

var knowledgeStore = new MemKnowledgeStore();

try (var agentican = Agentican.builder()
        .config(config)
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

When a `KnowledgeStore` is configured, the framework wires a `KnowledgeIngestor` as a `TaskListener`. On every completed step, if the step's output contains the marker string `KNOWLEDGE_ACQUIRED`, the ingestor:

1. Strips the marker from the output
2. Calls a `KnowledgeExtractor` (default: `LlmKnowledgeExtractor`) with `(step input, step output, existing indexed entries)`
3. Applies each extracted entry as `CREATE` (new entry) or `UPDATE` (merge facts into an existing entry)
4. Saves the resulting entries to the store with status `INDEXED`

This runs asynchronously on the task executor — it does not block the agent loop.

The agent opts in by including `KNOWLEDGE_ACQUIRED` in its final step output when it has learned something worth retaining. Your agent's `role` / system prompt should instruct it when to emit the marker.

`LlmKnowledgeExtractor` uses the framework's default LLM (whichever is registered under `LlmConfig.DEFAULT`). Supply your own `KnowledgeExtractor` implementation to customize extraction:

```java
public interface KnowledgeExtractor {
    KnowledgeExtraction extract(
            String input,
            String output,
            List<KnowledgeEntrySummary> existingEntries);
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
entry.addFile(KnowledgeFile);
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

    KnowledgeExtraction extract(
            String input,
            String output,
            List<KnowledgeEntrySummary> existingEntries);
}
```

### LlmKnowledgeExtractor

```java
new LlmKnowledgeExtractor(LlmClient llm);
```

## Next Steps

- [Tools & Toolkits](tools.md) — how `KnowledgeToolkit` fits into the toolkit model
- [Concepts](concepts.md) — overall architecture
- [Examples](examples.md) — recipes including knowledge usage
