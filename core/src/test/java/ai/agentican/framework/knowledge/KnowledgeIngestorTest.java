package ai.agentican.framework.knowledge;

import ai.agentican.framework.event.StepCompleted;
import ai.agentican.framework.orchestration.execution.WorkflowRunStatus;
import ai.agentican.framework.store.WorkflowRunStoreMemory;

import ai.agentican.framework.store.KnowledgeStoreMemory;
import ai.agentican.framework.state.RuntimeOwner;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class KnowledgeIngestorTest {

    private static final List<ExtractedEntry> EMPTY = List.of();

    @Test
    void createsMultipleEntriesFromSingleStep() {

        var state = newStateWithCompletedStep("t", "s", "research-step", "research text\n\nKNOWLEDGE_ACQUIRED");

        var store = new KnowledgeStoreMemory();

        KnowledgeExtractor extractor = (input, output, existing) -> List.<ExtractedEntry>of(
                new ExtractedEntry(ExtractedEntry.Action.CREATE, null, "Claude Opus 4.6",
                        "Anthropic model.", List.of(KnowledgeFact.of("pricing", "$15/$75", List.of("anthropic")))),
                new ExtractedEntry(ExtractedEntry.Action.CREATE, null, "Gemini 3.1 Pro",
                        "Google model.", List.of(KnowledgeFact.of("pricing", "$2/$12", List.of("google")))));

        trigger(state, store, extractor, "t", "s");

        var entries = store.indexed();
        assertEquals(2, entries.size());
        var names = entries.stream().map(KnowledgeEntry::name).toList();
        assertTrue(names.contains("Claude Opus 4.6"));
        assertTrue(names.contains("Gemini 3.1 Pro"));
    }

    @Test
    void updateAppendsNewFactsToExistingEntry() {

        var state = newStateWithCompletedStep("t", "s", "deep-dive", "more research\n\nKNOWLEDGE_ACQUIRED");

        var store = new KnowledgeStoreMemory();

        var existing = KnowledgeEntry.of("Claude Opus 4.6", "Existing entry");
        existing.addFact(KnowledgeFact.of("existing-1", "old fact 1", List.of("anthropic")));
        existing.setStatus(KnowledgeStatus.INDEXED);
        store.save(existing);

        KnowledgeExtractor extractor = (input, output, existingSummaries) -> List.<ExtractedEntry>of(
                new ExtractedEntry(ExtractedEntry.Action.UPDATE, existing.id(), null, null,
                        List.of(KnowledgeFact.of("new-1", "new fact 1", List.of("anthropic")))));

        trigger(state, store, extractor, "t", "s");

        var updated = store.get(existing.id());
        assertEquals(2, updated.facts().size(), "Update should append, not replace");
        var factNames = updated.facts().stream().map(KnowledgeFact::name).toList();
        assertTrue(factNames.contains("existing-1"));
        assertTrue(factNames.contains("new-1"));
    }

    @Test
    void skipsWhenExtractorReturnsEmpty() {

        var state = newStateWithCompletedStep("t", "s", "action-step", "I created a page.");

        var store = new KnowledgeStoreMemory();

        KnowledgeExtractor extractor = (input, output, existing) -> EMPTY;

        trigger(state, store, extractor, "t", "s");

        assertTrue(store.indexed().isEmpty());
    }

    @Test
    void skipsWhenStepFailed() {

        var state = new WorkflowRunStoreMemory();
        state.taskStarted("t", "demo", null, Map.of(), RuntimeOwner.IN_PROCESS, null);
        state.stepStarted("t", "s", "failed-step");
        state.runStarted("t", "s", "r", "agent");
        state.stepCompleted("t", "s", WorkflowRunStatus.FAILED, "some output");

        var store = new KnowledgeStoreMemory();

        KnowledgeExtractor extractor = (input, output, existing) -> List.<ExtractedEntry>of(
                new ExtractedEntry(ExtractedEntry.Action.CREATE, null, "X", "desc",
                        List.of(KnowledgeFact.of("f", "v", List.of()))));

        trigger(state, store, extractor, "t", "s");

        assertTrue(store.indexed().isEmpty(), "Failed steps must not be indexed");
    }

    @Test
    void skipsLoopOrBranchAggregates() {

        var state = new WorkflowRunStoreMemory();
        state.taskStarted("t", "demo", null, Map.of(), RuntimeOwner.IN_PROCESS, null);
        state.stepStarted("t", "s", "loop-step");

        state.stepCompleted("t", "s", WorkflowRunStatus.COMPLETED, "## Iteration 1\n\nfoo");

        var store = new KnowledgeStoreMemory();

        KnowledgeExtractor extractor = (input, output, existing) -> List.<ExtractedEntry>of(
                new ExtractedEntry(ExtractedEntry.Action.CREATE, null, "X", "desc",
                        List.of(KnowledgeFact.of("f", "v", List.of()))));

        trigger(state, store, extractor, "t", "s");

        assertTrue(store.indexed().isEmpty(), "Loop aggregates must not be indexed");
    }

    @Test
    void updateSkippedWhenTargetMissing() {

        var state = newStateWithCompletedStep("t", "s", "step", "text\n\nKNOWLEDGE_ACQUIRED");

        var store = new KnowledgeStoreMemory();

        KnowledgeExtractor extractor = (input, output, existing) -> List.<ExtractedEntry>of(
                new ExtractedEntry(ExtractedEntry.Action.UPDATE, "does-not-exist", null, null,
                        List.of(KnowledgeFact.of("f", "v", List.of()))));

        trigger(state, store, extractor, "t", "s");

        assertTrue(store.indexed().isEmpty(), "Update with missing target should be silently skipped");
    }

    @Test
    void existingEntriesSnapshotPassedToExtractor() {

        var state = newStateWithCompletedStep("t", "s", "step", "text\n\nKNOWLEDGE_ACQUIRED");

        var store = new KnowledgeStoreMemory();

        var seed = KnowledgeEntry.of("Seed Topic", "seed desc");
        seed.addFact(KnowledgeFact.of("f", "v", List.of()));
        seed.setStatus(KnowledgeStatus.INDEXED);
        store.save(seed);

        var seenExistingIds = new java.util.ArrayList<String>();

        KnowledgeExtractor extractor = (input, output, existing) -> {
            for (var e : existing) seenExistingIds.add(e.id());
            return EMPTY;
        };

        trigger(state, store, extractor, "t", "s");

        assertEquals(1, seenExistingIds.size());
        assertEquals(seed.id(), seenExistingIds.getFirst());
    }

    @Test
    void skipsWhenOutputHasNoAcquiredMarker() {

        var state = newStateWithCompletedStep("t", "s", "recall-only",
                "This output only reformats recalled knowledge — no marker here.");

        var store = new KnowledgeStoreMemory();

        var extractorCalled = new java.util.concurrent.atomic.AtomicBoolean(false);

        KnowledgeExtractor extractor = (input, output, existing) -> {
            extractorCalled.set(true);
            return List.<ExtractedEntry>of(
                    new ExtractedEntry(ExtractedEntry.Action.CREATE, null, "x", "y",
                            List.of(KnowledgeFact.of("f", "v", List.of()))));
        };

        trigger(state, store, extractor, "t", "s");

        assertFalse(extractorCalled.get(), "Extractor must not be called when marker is absent");
        assertTrue(store.indexed().isEmpty());
    }

    @Test
    void stripsMarkerBeforeCallingExtractor() {

        var state = newStateWithCompletedStep("t", "s", "research",
                "Paris is the capital of France.\n\nKNOWLEDGE_ACQUIRED");

        var store = new KnowledgeStoreMemory();

        var sawOutput = new java.util.concurrent.atomic.AtomicReference<String>();

        KnowledgeExtractor extractor = (input, output, existing) -> {
            sawOutput.set(output);
            return EMPTY;
        };

        trigger(state, store, extractor, "t", "s");

        assertNotNull(sawOutput.get());
        assertFalse(sawOutput.get().contains("KNOWLEDGE_ACQUIRED"),
                "Marker should be stripped from the text passed to the extractor");
        assertTrue(sawOutput.get().contains("Paris is the capital"),
                "Actual content should be preserved");
    }

    private static WorkflowRunStoreMemory newStateWithCompletedStep(String taskId, String stepId,
                                                                    String stepName, String output) {

        var state = new WorkflowRunStoreMemory();
        state.taskStarted(taskId, "demo", null, Map.of(), RuntimeOwner.IN_PROCESS, null);
        state.stepStarted(taskId, stepId, stepName);
        state.runStarted(taskId, stepId, "run-" + stepId, "agent");
        state.stepCompleted(taskId, stepId, WorkflowRunStatus.COMPLETED, output);
        return state;
    }

    /**
     * Build a StepCompleted event from the test's state and dispatch it to a new
     * ingestor. Matches the old {@code onStepCompleted(taskId, stepId)} convenience
     * the listener interface used to provide — but constructs the event from the
     * persisted step (status, stepName, output) so the ingestor's switch sees
     * what it would in production.
     */
    private static void trigger(WorkflowRunStoreMemory state, KnowledgeStoreMemory store,
                                KnowledgeExtractor extractor, String taskId, String stepId) {

        var stepLog = state.load(taskId).findStepById(stepId);

        new KnowledgeIngestor(store, extractor, Runnable::run)
                .on(new StepCompleted(taskId, stepId, stepLog.stepName(),
                        stepLog.status(), stepLog.output()));
    }
}
