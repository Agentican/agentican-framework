package ai.agentican.framework.vector;

import ai.agentican.framework.vector.VectorHit;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VectorIndexRegistryTest {

    private static VectorIndex stub(String name) {

        return new VectorIndex() {
            @Override public String      name()        { return name; }
            @Override public String      description() { return ""; }
            @Override public IndexResult index(String text, Map<String, String> meta) {
                return new IndexResult(0, List.of());
            }
            @Override public List<VectorHit> retrieve(String query, int k) {
                return List.of();
            }
        };
    }

    @Test
    void registerAndLookup() {

        var registry = new VectorIndexRegistry();
        var kb       = stub("docs");
        registry.register(kb);

        assertSame(kb, registry.get("docs"));
        assertTrue(registry.contains("docs"));
        assertEquals(1, registry.size());
        assertFalse(registry.isEmpty());
        assertTrue(registry.names().contains("docs"));
    }

    @Test
    void emptyRegistry() {

        var registry = new VectorIndexRegistry();

        assertTrue(registry.isEmpty());
        assertEquals(0, registry.size());
        assertNull(registry.get("missing"));
        assertFalse(registry.contains("missing"));
    }

    @Test
    void nullKbThrows() {

        assertThrows(IllegalArgumentException.class,
                () -> new VectorIndexRegistry().register(null));
    }

    @Test
    void blankNameThrows() {

        var registry = new VectorIndexRegistry();

        assertThrows(IllegalArgumentException.class, () -> registry.register(stub("")));
        assertThrows(IllegalArgumentException.class, () -> registry.register(stub(" ")));
    }

    @Test
    void duplicateNameThrows() {

        var registry = new VectorIndexRegistry();
        registry.register(stub("docs"));

        assertThrows(IllegalStateException.class, () -> registry.register(stub("docs")));
    }
}
