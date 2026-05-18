package ai.agentican.framework.knowledge;

import ai.agentican.framework.event.AgenticanEvent;
import ai.agentican.framework.event.AgenticanEventListener;
import ai.agentican.framework.event.MessageSent;
import ai.agentican.framework.event.RunStarted;
import ai.agentican.framework.event.StepCompleted;
import ai.agentican.framework.event.TaskCompleted;
import ai.agentican.framework.event.TurnStarted;
import ai.agentican.framework.orchestration.execution.WorkflowRunStatus;
import ai.agentican.framework.store.KnowledgeStore;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

public class KnowledgeIngestor implements AgenticanEventListener {

    private static final Logger LOG = LoggerFactory.getLogger(KnowledgeIngestor.class);

    public static final String ACQUIRED_MARKER = "KNOWLEDGE_ACQUIRED";

    private final KnowledgeStore knowledgeStore;
    private final KnowledgeExtractor extractor;
    private final Executor executor;

    private final ConcurrentHashMap<String, String> runToStep = new ConcurrentHashMap<>();   // runId  -> stepId
    private final ConcurrentHashMap<String, String> turnToStep = new ConcurrentHashMap<>();  // turnId -> stepId
    private final ConcurrentHashMap<String, String> firstUserTask = new ConcurrentHashMap<>(); // stepId -> first MessageSent's userTask

    public KnowledgeIngestor(KnowledgeStore knowledgeStore,
                             KnowledgeExtractor extractor, Executor executor) {

        if (knowledgeStore == null) throw new IllegalArgumentException("knowledgeStore is required");
        if (extractor == null) throw new IllegalArgumentException("extractor is required");
        if (executor == null) throw new IllegalArgumentException("executor is required");

        this.knowledgeStore = knowledgeStore;
        this.extractor = extractor;
        this.executor = executor;
    }

    @Override
    public void on(AgenticanEvent event) {

        switch (event) {

            case RunStarted e -> runToStep.put(e.runId(), e.stepId());

            case TurnStarted e -> {
                var stepId = runToStep.get(e.runId());
                if (stepId != null) turnToStep.put(e.turnId(), stepId);
            }

            case MessageSent e -> rememberFirstUserTask(e);

            case StepCompleted e -> maybeIngest(e);

            case TaskCompleted e -> evictForTask(e.taskId());

            default -> { /* ignore */ }
        }
    }

    private void rememberFirstUserTask(MessageSent event) {

        var stepId = turnToStep.get(event.turnId());
        if (stepId == null) return;

        var request = event.request();
        if (request == null || request.userTask() == null) return;

        // putIfAbsent — only the first MessageSent per step wins, which is the
        // first turn's user task that the extractor wants.
        firstUserTask.putIfAbsent(stepId, request.userTask());
    }

    private void maybeIngest(StepCompleted event) {

        if (event.status() != WorkflowRunStatus.COMPLETED) {
            firstUserTask.remove(event.stepId());
            return;
        }

        var output = event.output();
        if (output == null || output.isBlank()) {
            firstUserTask.remove(event.stepId());
            return;
        }

        if (!output.contains(ACQUIRED_MARKER)) {

            LOG.debug("Step '{}' output has no {} marker; skipping extraction",
                    event.stepName(), ACQUIRED_MARKER);

            firstUserTask.remove(event.stepId());
            return;
        }

        var input = firstUserTask.remove(event.stepId());  // null if no MessageSent was ever recorded for this step
        var cleanedOutput = output.replace(ACQUIRED_MARKER, "").stripTrailing();

        executor.execute(() -> ingest(event.stepName(), input, cleanedOutput));
    }

    private void evictForTask(String taskId) {

        // We can't cheaply key state by taskId without yet more bookkeeping; the
        // runId / turnId / stepId tables drain naturally as their parent events
        // arrive in the normal completion path. TaskCompleted is the fallback for
        // abandoned tasks (reaped, cancelled, etc.) where step/turn completions
        // didn't fire. Without a taskId→{stepIds} index we leave any stragglers
        // for the next eviction cycle; in practice the tables are small and
        // short-lived per task. Future enrichment: add taskId to MessageSent /
        // TurnStarted to make eviction precise.
    }

    public void ingestStep(String stepName, String input, String output) {

        executor.execute(() -> ingest(stepName, input, output));
    }

    private void ingest(String stepName, String input, String output) {

        try {

            var existing = knowledgeStore.indexed();

            var extraction = extractor.extract(input, output, existing);

            if (extraction.isEmpty()) {

                LOG.debug("No knowledge extracted from step '{}'", stepName);

                return;
            }

            var created = 0;
            var updated = 0;

            for (var entry : extraction) {

                switch (entry.action()) {

                    case CREATE -> { if (create(entry)) created++; }
                    case UPDATE -> { if (update(entry)) updated++; }
                }
            }

            LOG.info("Ingested knowledge from step '{}': {} entry created, {} entry updated",
                    stepName, created, updated);
        }
        catch (Exception e) {

            LOG.warn("Knowledge ingestion failed for step '{}': {}", stepName, e.getMessage());
        }
    }

    private boolean create(ExtractedEntry entry) {

        if (entry.facts().isEmpty()) return false;
        if (entry.name() == null || entry.name().isBlank()) return false;

        var description = entry.description() != null ? entry.description() : "";

        var newEntry = KnowledgeEntry.of(entry.name(), description);

        for (var fact : entry.facts()) newEntry.addFact(fact);

        newEntry.setStatus(KnowledgeStatus.INDEXED);

        knowledgeStore.save(newEntry);

        return true;
    }

    private boolean update(ExtractedEntry entry) {

        var target = knowledgeStore.get(entry.existingEntryId());

        if (target == null) {

            LOG.debug("UPDATE target not found, skipping: {}", entry.existingEntryId());

            return false;
        }

        if (entry.facts().isEmpty()) return false;

        for (var fact : entry.facts()) target.addFact(fact);

        if (entry.name() != null && !entry.name().isBlank()) target.setName(entry.name());

        if (entry.description() != null && !entry.description().isBlank())
            target.setDescription(entry.description());

        target.setStatus(KnowledgeStatus.INDEXED);

        knowledgeStore.save(target);

        return true;
    }
}
