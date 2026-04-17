package ai.agentican.quarkus.store.jpa;

import ai.agentican.framework.knowledge.KnowledgeEntry;
import ai.agentican.framework.knowledge.KnowledgeFact;
import ai.agentican.framework.knowledge.KnowledgeStatus;
import ai.agentican.framework.knowledge.KnowledgeStore;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class JpaKnowledgeStoreTest {

    @Inject
    JpaKnowledgeStore store;

    @Inject
    KnowledgeStore storeInterface;

    @Test
    void interfaceResolvesToJpaBean() {

        assertSame(store, storeInterface,
                "KnowledgeStore interface should resolve to JpaKnowledgeStore when backend property is missing");
    }

    @Test
    void roundTripsCreateAndRead() {

        var entry = new KnowledgeEntry("k-" + System.nanoTime(), "Claude Opus 4.6", "frontier model");
        entry.setStatus(KnowledgeStatus.INDEXED);
        entry.addFact(KnowledgeFact.of("GPQA", "91.3% on GPQA Diamond", List.of("anthropic/claude/benchmarks")));
        entry.addFact(KnowledgeFact.of("Pricing", "$15/$75 per million tokens", List.of("anthropic/claude/pricing")));

        store.save(entry);

        var fetched = store.get(entry.id());
        assertNotNull(fetched);
        assertEquals("Claude Opus 4.6", fetched.name());
        assertEquals("frontier model", fetched.description());
        assertEquals(KnowledgeStatus.INDEXED, fetched.status());
        assertEquals(2, fetched.facts().size());
    }

    @Test
    void saveReplacesFactsOnUpdate() {

        var entry = new KnowledgeEntry("k-replace-" + System.nanoTime(), "Entry", "desc");
        entry.setStatus(KnowledgeStatus.INDEXED);
        entry.addFact(KnowledgeFact.of("A", "fact A", List.of("x")));
        store.save(entry);

        var reload = store.get(entry.id());
        reload.clearFacts();
        reload.addFact(KnowledgeFact.of("B", "fact B", List.of("y")));
        reload.addFact(KnowledgeFact.of("C", "fact C", List.of("z")));
        store.save(reload);

        var after = store.get(entry.id());
        assertEquals(2, after.facts().size());
        var names = after.facts().stream().map(KnowledgeFact::name).toList();
        assertTrue(names.contains("B"));
        assertTrue(names.contains("C"));
        assertFalse(names.contains("A"), "old facts should be replaced");
    }

    @Test
    void indexedFiltersByStatus() {

        var indexed = new KnowledgeEntry("k-idx-" + System.nanoTime(), "Indexed", "");
        indexed.setStatus(KnowledgeStatus.INDEXED);
        store.save(indexed);

        var pending = new KnowledgeEntry("k-pend-" + System.nanoTime(), "Pending", "");
        pending.setStatus(KnowledgeStatus.INDEXING);
        store.save(pending);

        var idList = store.indexed().stream().map(KnowledgeEntry::id).toList();
        assertTrue(idList.contains(indexed.id()));
        assertFalse(idList.contains(pending.id()));
    }

    @Test
    void deleteRemovesEntryAndFacts() {

        var entry = new KnowledgeEntry("k-del-" + System.nanoTime(), "Doomed", "");
        entry.setStatus(KnowledgeStatus.INDEXED);
        entry.addFact(KnowledgeFact.of("F", "v", List.of("t")));
        store.save(entry);
        assertNotNull(store.get(entry.id()));

        store.delete(entry.id());
        assertNull(store.get(entry.id()));
    }

    @Test
    void tagsRoundTripThroughJson() {

        var entry = new KnowledgeEntry("k-tags-" + System.nanoTime(), "Tags entry", "");
        entry.setStatus(KnowledgeStatus.INDEXED);
        entry.addFact(KnowledgeFact.of("F", "v", List.of("a/b/c", "x/y")));
        store.save(entry);

        var fetched = store.get(entry.id());
        assertEquals(List.of("a/b/c", "x/y"), fetched.facts().getFirst().tags());
    }
}
