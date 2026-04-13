package ai.agentican.framework.knowledge;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class KnowledgeEntryTest {

    @Test
    void createWithDefaults() {

        var entry = new KnowledgeEntry("id-1", "My Entry", "A description");

        assertEquals("id-1", entry.id());
        assertEquals("My Entry", entry.name());
        assertEquals("A description", entry.description());
        assertEquals(KnowledgeStatus.INDEXING, entry.status());
        assertTrue(entry.facts().isEmpty());
        assertTrue(entry.files().isEmpty());
        assertNotNull(entry.created());
        assertNotNull(entry.updated());
    }

    @Test
    void addFactAndFile() throws InterruptedException {

        var entry = new KnowledgeEntry("id", "name", "desc");
        var initialUpdated = entry.updated();

        Thread.sleep(5);

        var fact = Fact.of("Pricing", "Costs $10", List.of("pricing"));
        entry.addFact(fact);

        assertEquals(1, entry.facts().size());
        assertEquals("Pricing", entry.facts().getFirst().name());
        assertTrue(entry.updated().isAfter(initialUpdated));

        var afterFactUpdated = entry.updated();
        Thread.sleep(5);

        var file = KnowledgeFile.of("doc.pdf", "application/pdf", new byte[]{1, 2, 3});
        entry.addFile(file);

        assertEquals(1, entry.files().size());
        assertEquals("doc.pdf", entry.files().getFirst().name());
        assertTrue(entry.updated().isAfter(afterFactUpdated));
    }

    @Test
    void setStatus() {

        var entry = new KnowledgeEntry("id", "name", "desc");

        assertEquals(KnowledgeStatus.INDEXING, entry.status());

        entry.setStatus(KnowledgeStatus.INDEXED);

        assertEquals(KnowledgeStatus.INDEXED, entry.status());
    }

    @Test
    void clearFacts() {

        var entry = new KnowledgeEntry("id", "name", "desc");

        entry.addFact(Fact.of("f1", "c1", List.of()));
        entry.addFact(Fact.of("f2", "c2", List.of()));

        assertEquals(2, entry.facts().size());

        entry.clearFacts();

        assertTrue(entry.facts().isEmpty());
    }

    @Test
    void requiresIdAndName() {

        assertThrows(IllegalArgumentException.class,
                () -> new KnowledgeEntry(null, "name", "desc"));

        assertThrows(IllegalArgumentException.class,
                () -> new KnowledgeEntry("", "name", "desc"));

        assertThrows(IllegalArgumentException.class,
                () -> new KnowledgeEntry("id", null, "desc"));

        assertThrows(IllegalArgumentException.class,
                () -> new KnowledgeEntry("id", "", "desc"));
    }
}
