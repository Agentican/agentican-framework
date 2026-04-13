# Agent Knowledge

Agent knowledge is a persistent, structured memory store that agents can recall during task execution. Unlike the per-task `ScratchpadToolkit`, knowledge entries survive across tasks and are shared across all agents.

## Concepts

### KnowledgeEntry

A topical container with extracted facts:

```
KnowledgeEntry
  ├── id, name, description
  ├── status: INDEXING | INDEXED | FAILED
  ├── files: List<KnowledgeFile>      ← optional source documents
  ├── facts: List<Fact>                ← extracted atomic facts
  └── created, updated
```

Only entries with status `INDEXED` appear in the agent's knowledge index. Use `INDEXING` while you're populating an entry, then flip to `INDEXED` when ready.

### Fact

An atomic, taggable piece of knowledge:

```
Fact
  ├── id, name, content
  ├── tags: List<String>      ← supports hierarchical "domain/topic/sub"
  └── created, updated
```

### KnowledgeFile

Optional source document attached to an entry:

```
KnowledgeFile
  ├── id, name, type, size
  └── contents: byte[]
```

The framework doesn't extract facts from binary files (PDFs, Office docs, images) — that's app-specific. You can extract facts yourself and call `entry.addFact(...)`, or implement your own `FactExtractor` that handles your formats.

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
    @Override public List<KnowledgeEntry> indexed() { /* list where status=INDEXED */ }
    @Override public void delete(String id) { /* delete */ }
}
```

## Adding Knowledge

Create entries and populate them with facts:

```java
var entry = KnowledgeEntry.of("Q1 Pricing", "Pricing decisions for Q1 2026");

entry.addFact(Fact.of(
    "Pro plan price",
    "Pro tier is $49/month effective Jan 1, 2026.",
    List.of("pricing/saas", "decision/q1")));

entry.addFact(Fact.of(
    "Free trial length",
    "All new signups get a 14-day free trial.",
    List.of("pricing/trial", "policy")));

entry.setStatus(KnowledgeStatus.INDEXED);
knowledgeStore.save(entry);
```

Once `INDEXED`, the entry appears in the agent's knowledge index automatically.

## How Agents Use Knowledge

When a `KnowledgeStore` is provided:

1. **Index in user message** — every agent turn renders an `<knowledge-base><index>` section listing all `INDEXED` entries (id, name, description) so the agent knows what's available.

2. **Recall tool** — the framework automatically registers a `RECALL_KNOWLEDGE` tool the agent can call:

   ```
   RECALL_KNOWLEDGE(entry_ids: ["abc-123", "def-456"])
     → returns full entries with facts and tags
   ```

3. **Recalled section** — once the agent recalls entries, they appear in subsequent turns under `<knowledge-base><recalled>` with full facts. The agent can use them in its reasoning without re-recalling.

The agent decides what's relevant. The system prompt tells it: scan the index, recall what looks useful, and only do fresh research if the topic isn't covered.

## Extracting Facts from Text

The framework includes `LlmFactExtractor` for extracting structured facts from arbitrary text using an LLM:

```java
var extractor = new LlmFactExtractor(myLlmClient);

var text = """
    Our Pro plan is $49/month. We just rolled out a 14-day free trial for new signups.
    Customer retention has improved 12% since the policy change.
    """;

List<Fact> facts = extractor.extractFromText(text);

// Save them in an entry
var entry = KnowledgeEntry.of("Pricing notes", "From the Q1 review");
facts.forEach(entry::addFact);
entry.setStatus(KnowledgeStatus.INDEXED);
knowledgeStore.save(entry);
```

The extractor uses a built-in prompt that asks the LLM to identify atomic facts and tag them hierarchically. Failures fall back to an empty list (no exception).

For other content types (PDFs, images, Office docs), implement `FactExtractor` yourself:

```java
public class PdfFactExtractor implements FactExtractor {

    @Override
    public List<Fact> extractFromText(String text) {
        // your extraction logic
    }
}
```

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
| Scope | Single agent | Shared across agents/tasks |
| Structure | Key/value | Entries → facts → tags |
| Indexed | No | Yes (visible in every prompt) |
| Recall | Direct key lookup | `RECALL_KNOWLEDGE` tool with ids |
| Persistence | In-memory only | Pluggable `KnowledgeStore` |

Both are available simultaneously. Use scratchpad for working memory within a task and knowledge for facts you want to retain across tasks.

## Storing Knowledge from Agent Output

A common pattern: have agents extract facts from their own work and save them for future tasks.

```java
var result = agentican.run("Research the top 3 LLM providers and their pricing").result();

// Extract facts from the final output and save
var extractor = new LlmFactExtractor(llmClient);
var facts = extractor.extractFromText(result.lastOutput());

if (!facts.isEmpty()) {

    var entry = KnowledgeEntry.of("LLM Provider Pricing", "Researched on " + LocalDate.now());
    facts.forEach(entry::addFact);
    entry.setStatus(KnowledgeStatus.INDEXED);
    knowledgeStore.save(entry);
}

// Future tasks will see this entry in the index
```

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
KnowledgeEntry.of(String name, String description);  // generates UUID

entry.addFact(Fact);
entry.addFile(KnowledgeFile);
entry.setStatus(KnowledgeStatus);
entry.setName(String);
entry.setDescription(String);
entry.clearFacts();
```

### Fact

```java
new Fact(String id, String name, String content, List<String> tags, Instant created, Instant updated);
Fact.of(String name, String content, List<String> tags);  // generates UUID
```

### FactExtractor

```java
public interface FactExtractor {
    List<Fact> extractFromText(String text);
}
```

### LlmFactExtractor

```java
new LlmFactExtractor(LlmClient llm);
```

## Next Steps

- [Tools & Toolkits](tools.md) — how `KnowledgeToolkit` fits into the toolkit model
- [Concepts](concepts.md) — overall architecture
- [Examples](examples.md) — recipes including knowledge usage
