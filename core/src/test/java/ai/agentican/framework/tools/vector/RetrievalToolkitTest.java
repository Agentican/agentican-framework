package ai.agentican.framework.tools.vector;

import ai.agentican.framework.vector.IndexResult;
import ai.agentican.framework.vector.VectorIndex;
import ai.agentican.framework.vector.VectorIndexRegistry;
import ai.agentican.framework.vector.VectorHit;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RetrievalToolkitTest {

    private static VectorIndex stub(String name, String description, List<VectorHit> hits) {

        return new VectorIndex() {
            @Override public String      name()        { return name; }
            @Override public String      description() { return description; }
            @Override public IndexResult index(String text, Map<String, String> meta) {
                return new IndexResult(0, List.of());
            }
            @Override public List<VectorHit> retrieve(String query, int k) {
                return hits.stream().limit(k).toList();
            }
        };
    }

    @Test
    void oneToolPerVectorIndex() {

        var registry = new VectorIndexRegistry();
        registry.register(stub("docs",    "Product docs",    List.of()));
        registry.register(stub("support", "Support tickets", List.of()));

        var toolkit = new RetrievalToolkit(registry);
        var names   = toolkit.tools().stream().map(t -> t.name()).toList();

        assertEquals(2, names.size());
        assertTrue(names.contains("search_docs"));
        assertTrue(names.contains("search_support"));
    }

    @Test
    void toolDescriptionIncludesVectorIndexDescription() {

        var registry = new VectorIndexRegistry();
        registry.register(stub("docs", "Product documentation and API references", List.of()));

        var tool = new RetrievalToolkit(registry).tools().getFirst();

        assertTrue(tool.description().contains("Product documentation"),
                "tool description must include KB description: " + tool.description());
        assertTrue(tool.description().contains("'docs'"));
    }

    @Test
    void handlesAcceptsKnownPrefixedName() {

        var registry = new VectorIndexRegistry();
        registry.register(stub("docs", "", List.of()));

        var toolkit = new RetrievalToolkit(registry);

        assertTrue(toolkit.handles("search_docs"));
        assertFalse(toolkit.handles("search_unknown"));
        assertFalse(toolkit.handles("docs"));
        assertFalse(toolkit.handles("search_"));
        assertFalse(toolkit.handles(null));
    }

    @Test
    void executeReturnsJsonHits() throws Exception {

        var hits = List.of(
                new VectorHit("a", 0.99f, "alpha content", Map.of("src", "a-source")),
                new VectorHit("b", 0.80f, "beta content",  Map.of()));

        var registry = new VectorIndexRegistry();
        registry.register(stub("docs", "", hits));

        var toolkit = new RetrievalToolkit(registry);
        var json    = toolkit.execute("search_docs", Map.of("query", "any", "k", 2));

        var parsed = (Map<String, Object>) new ObjectMapper().readValue(json, Map.class);
        var list   = (List<Map<String, Object>>) parsed.get("hits");

        assertEquals(2, list.size());
        assertEquals("a",             list.get(0).get("id"));
        assertEquals("alpha content", list.get(0).get("content"));
        assertEquals("a-source",      ((Map<?, ?>) list.get(0).get("metadata")).get("src"));
    }

    @Test
    void executeUsesDefaultKWhenMissing() {

        var hits = List.of(
                new VectorHit("a", 0.9f, "x", Map.of()),
                new VectorHit("b", 0.8f, "y", Map.of()),
                new VectorHit("c", 0.7f, "z", Map.of()),
                new VectorHit("d", 0.6f, "w", Map.of()),
                new VectorHit("e", 0.5f, "v", Map.of()),
                new VectorHit("f", 0.4f, "u", Map.of()));

        var registry = new VectorIndexRegistry();
        registry.register(stub("docs", "", hits));

        var json = new RetrievalToolkit(registry).execute("search_docs", Map.of("query", "any"));

        assertTrue(json.contains("\"e\""));
        assertFalse(json.contains("\"f\""));
    }

    @Test
    void invalidKbNameRejectedAtConstruction() {

        var registry = new VectorIndexRegistry();
        registry.register(stub("has spaces", "", List.of()));

        assertThrows(IllegalArgumentException.class, () -> new RetrievalToolkit(registry));
    }

    @Test
    void emptyRegistryProducesNoTools() {

        var toolkit = new RetrievalToolkit(new VectorIndexRegistry());

        assertTrue(toolkit.tools().isEmpty());
        assertFalse(toolkit.handles("search_docs"));
    }

    @Test
    void registryRequired() {

        assertThrows(IllegalArgumentException.class, () -> new RetrievalToolkit(null));
    }
}
