package ai.agentican.framework.vector;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public abstract class VectorStoreContractTest {

    protected static final String ID_A = "00000000-0000-0000-0000-00000000000a";
    protected static final String ID_B = "00000000-0000-0000-0000-00000000000b";
    protected static final String ID_C = "00000000-0000-0000-0000-00000000000c";

    protected abstract VectorStore newStore(int dimensions);

    @Test
    void upsertThenSearchReturnsHit() {

        var store = newStore(3);
        var v     = new float[]{1f, 0f, 0f};
        store.upsert(List.of(new VectorRecord(ID_A, v, "first", Map.of("k", "v"))));

        var hits = store.search(v, 1);

        assertEquals(1,       hits.size());
        assertEquals(ID_A,    hits.getFirst().id());
        assertEquals("first", hits.getFirst().content());
        assertEquals("v",     hits.getFirst().metadata().get("k"));
    }

    @Test
    void searchOrdersBySimilarity() {

        var store = newStore(3);
        store.upsert(List.of(
                new VectorRecord(ID_A, new float[]{ 1f,  0f, 0f}, "near",     Map.of()),
                new VectorRecord(ID_B, new float[]{ 0f,  1f, 0f}, "far",      Map.of()),
                new VectorRecord(ID_C, new float[]{-1f,  0f, 0f}, "opposite", Map.of())));

        var hits = store.search(new float[]{1f, 0f, 0f}, 3);

        assertEquals(3,    hits.size());
        assertEquals(ID_A, hits.get(0).id());
    }

    @Test
    void emptyStoreSearchReturnsEmpty() {

        assertTrue(newStore(3).search(new float[]{1f, 0f, 0f}, 5).isEmpty());
    }

    @Test
    void deleteRemovesRecord() {

        var store = newStore(3);
        store.upsert(List.of(new VectorRecord(ID_A, new float[]{1f, 0f, 0f}, "x", Map.of())));
        store.delete(List.of(ID_A));

        assertTrue(store.search(new float[]{1f, 0f, 0f}, 5).isEmpty());
    }

    @Test
    void metadataRoundTrips() {

        var store = newStore(3);
        store.upsert(List.of(new VectorRecord(
                ID_A, new float[]{1f, 0f, 0f}, "content", Map.of("a", "1", "b", "two"))));

        var hits = store.search(new float[]{1f, 0f, 0f}, 1);

        assertEquals("1",   hits.getFirst().metadata().get("a"));
        assertEquals("two", hits.getFirst().metadata().get("b"));
    }
}
