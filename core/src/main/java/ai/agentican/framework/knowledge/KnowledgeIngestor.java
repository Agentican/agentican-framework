package ai.agentican.framework.knowledge;

import ai.agentican.framework.orchestration.execution.TaskListener;
import ai.agentican.framework.orchestration.execution.TaskStatus;
import ai.agentican.framework.store.TaskStateStore;

import ai.agentican.framework.store.KnowledgeStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executor;

public class KnowledgeIngestor implements TaskListener {

    private static final Logger LOG = LoggerFactory.getLogger(KnowledgeIngestor.class);

    static final String ACQUIRED_MARKER = "KNOWLEDGE_ACQUIRED";

    private final TaskStateStore taskStateStore;
    private final KnowledgeStore knowledgeStore;
    private final KnowledgeExtractor extractor;
    private final Executor executor;

    public KnowledgeIngestor(TaskStateStore taskStateStore, KnowledgeStore knowledgeStore,
                             KnowledgeExtractor extractor, Executor executor) {

        if (taskStateStore == null) throw new IllegalArgumentException("taskStateStore is required");
        if (knowledgeStore == null) throw new IllegalArgumentException("knowledgeStore is required");
        if (extractor == null) throw new IllegalArgumentException("extractor is required");
        if (executor == null) throw new IllegalArgumentException("executor is required");

        this.taskStateStore = taskStateStore;
        this.knowledgeStore = knowledgeStore;
        this.extractor = extractor;
        this.executor = executor;
    }

    @Override
    public void onStepCompleted(String taskId, String stepId) {

        var taskLog = taskStateStore.load(taskId);
        if (taskLog == null) return;

        var stepLog = taskLog.findStepById(stepId);
        if (stepLog == null) return;

        if (stepLog.status() != TaskStatus.COMPLETED) return;

        if (stepLog.runs().isEmpty()) return;

        var output = stepLog.output();
        if (output == null || output.isBlank()) return;

        if (!output.contains(ACQUIRED_MARKER)) {
            LOG.debug("Step '{}' output has no {} marker; skipping extraction",
                    stepLog.stepName(), ACQUIRED_MARKER);
            return;
        }

        var stepName = stepLog.stepName();

        var firstRun = stepLog.runs().getFirst();
        var firstTurn = firstRun.turns().isEmpty() ? null : firstRun.turns().getFirst();
        var input = firstTurn != null && firstTurn.request() != null
                ? firstTurn.request().userTask()
                : null;

        var cleanedOutput = output.replace(ACQUIRED_MARKER, "").stripTrailing();

        executor.execute(() -> ingest(stepName, input, cleanedOutput));
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

                    case CREATE -> {
                        if (create(entry)) created++;
                    }
                    case UPDATE -> {
                        if (update(entry)) updated++;
                    }
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
