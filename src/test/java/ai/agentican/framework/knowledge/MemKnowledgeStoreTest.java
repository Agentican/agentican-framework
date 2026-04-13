package ai.agentican.framework.knowledge;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MemKnowledgeStoreTest {

    @Test
    void saveAndGet() {

        var store = new MemKnowledgeStore();
        var entry = KnowledgeEntry.of("entry-1", "desc");

        store.save(entry);

        assertSame(entry, store.get(entry.id()));
    }

    @Test
    void getMissingReturnsNull() {

        var store = new MemKnowledgeStore();

        assertNull(store.get("nope"));
    }

    @Test
    void allReturnsAllEntries() {

        var store = new MemKnowledgeStore();

        store.save(KnowledgeEntry.of("a", "desc-a"));
        store.save(KnowledgeEntry.of("b", "desc-b"));
        store.save(KnowledgeEntry.of("c", "desc-c"));

        assertEquals(3, store.all().size());
    }

    @Test
    void indexedReturnsOnlyIndexed() {

        var store = new MemKnowledgeStore();

        var indexing = KnowledgeEntry.of("indexing", "desc");
        // default status is INDEXING

        var indexed = KnowledgeEntry.of("indexed", "desc");
        indexed.setStatus(KnowledgeStatus.INDEXED);

        var failed = KnowledgeEntry.of("failed", "desc");
        failed.setStatus(KnowledgeStatus.FAILED);

        store.save(indexing);
        store.save(indexed);
        store.save(failed);

        var result = store.indexed();

        assertEquals(1, result.size());
        assertEquals(indexed.id(), result.getFirst().id());
    }

    @Test
    void deleteRemovesEntry() {

        var store = new MemKnowledgeStore();
        var entry = KnowledgeEntry.of("entry", "desc");

        store.save(entry);
        store.delete(entry.id());

        assertNull(store.get(entry.id()));
        assertEquals(0, store.all().size());
    }

    @Test
    void saveOverwritesExisting() {

        var store = new MemKnowledgeStore();
        var entry = KnowledgeEntry.of("original", "desc");

        store.save(entry);

        entry.setName("updated");
        store.save(entry);

        var loaded = store.get(entry.id());

        assertNotNull(loaded);
        assertEquals("updated", loaded.name());
        assertEquals(1, store.all().size());
    }
}
