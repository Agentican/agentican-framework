package ai.agentican.framework.knowledge;

import ai.agentican.framework.store.KnowledgeStoreMemory;
import ai.agentican.framework.hitl.HitlType;
import ai.agentican.framework.tools.knowledge.KnowledgeToolkit;
import ai.agentican.framework.util.Json;

import com.fasterxml.jackson.core.type.TypeReference;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class KnowledgeToolkitTest {

    @SuppressWarnings("unchecked")
    private static Map<String, Object> parse(String json) throws Exception {

        return Json.mapper().readValue(json, new TypeReference<Map<String, Object>>() {});
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> entries(Map<String, Object> result) {

        return (List<Map<String, Object>>) result.get("entries");
    }

    @Test
    void recallSingleEntry() throws Exception {

        var store = new KnowledgeStoreMemory();
        var entry = new KnowledgeEntry("entry-1", "Customer Data", "All about customers");

        entry.addFact(KnowledgeFact.of("Fact One", "Content one", List.of("tag1")));
        entry.addFact(KnowledgeFact.of("Fact Two", "Content two", List.of("tag2")));
        entry.setStatus(KnowledgeStatus.INDEXED);
        store.save(entry);

        var toolkit = new KnowledgeToolkit(store);

        var result = toolkit.execute(KnowledgeToolkit.TOOL_NAME, Map.of("entry_ids", List.of("entry-1")));

        var parsed = parse(result);
        var entries = entries(parsed);

        assertEquals(1, entries.size());

        var recalled = entries.getFirst();

        assertEquals("entry-1", recalled.get("id"));
        assertEquals("Customer Data", recalled.get("name"));

        @SuppressWarnings("unchecked")
        var facts = (List<Map<String, Object>>) recalled.get("facts");

        assertEquals(2, facts.size());
        assertEquals("Fact One", facts.get(0).get("name"));
        assertEquals("Fact Two", facts.get(1).get("name"));
    }

    @Test
    void recallMultipleEntries() throws Exception {

        var store = new KnowledgeStoreMemory();

        var entry1 = new KnowledgeEntry("id-1", "Entry One", "d1");
        var entry2 = new KnowledgeEntry("id-2", "Entry Two", "d2");

        store.save(entry1);
        store.save(entry2);

        var toolkit = new KnowledgeToolkit(store);

        var result = toolkit.execute(KnowledgeToolkit.TOOL_NAME, Map.of("entry_ids", List.of("id-1", "id-2")));

        var entries = entries(parse(result));

        assertEquals(2, entries.size());
    }

    @Test
    void recallMissingEntryIgnored() throws Exception {

        var store = new KnowledgeStoreMemory();
        var toolkit = new KnowledgeToolkit(store);

        var result = toolkit.execute(KnowledgeToolkit.TOOL_NAME, Map.of("entry_ids", List.of("does-not-exist")));

        var entries = entries(parse(result));

        assertNotNull(entries);
        assertTrue(entries.isEmpty());
    }

    @Test
    void hitlTypeIsNone() {

        var toolkit = new KnowledgeToolkit(new KnowledgeStoreMemory());

        assertEquals(HitlType.NONE, toolkit.hitlType(KnowledgeToolkit.TOOL_NAME));
    }

    @Test
    void handlesOnlyRecallTool() {

        var toolkit = new KnowledgeToolkit(new KnowledgeStoreMemory());

        assertTrue(toolkit.handles(KnowledgeToolkit.TOOL_NAME));
        assertFalse(toolkit.handles("other"));
    }
}
