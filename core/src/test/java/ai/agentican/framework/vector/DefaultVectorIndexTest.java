package ai.agentican.framework.vector;

import ai.agentican.framework.embeddings.RecordingEmbeddingClient;
import ai.agentican.framework.vector.RecordingVectorStore;
import ai.agentican.framework.vector.VectorHit;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultVectorIndexTest {

    @Test
    void indexChunksEmbedsAndUpserts() {

        var emb     = new RecordingEmbeddingClient(8);
        var store   = new RecordingVectorStore(8);
        var chunker = new SlidingChunker(10, 2);
        var kb      = new DefaultVectorIndex("docs", "test", emb, store, chunker);

        var text   = "The quick brown fox jumps over the lazy dog and lands";
        var result = kb.index(text, Map.of("source", "test"));

        assertTrue(result.chunkCount() > 1);
        assertEquals(result.chunkCount(), result.ids().size());
        assertEquals(result.chunkCount(), store.upserts.size());
        assertEquals(1, emb.calls.size());
        assertEquals(result.chunkCount(), emb.calls.getFirst().size());

        for (var rec : store.upserts) {
            assertEquals("test", rec.metadata().get("source"));
            assertEquals(8, rec.vector().length);
            assertNotNull(rec.id());
        }
    }

    @Test
    void emptyTextProducesNoUpsertOrEmbeddingCall() {

        var emb   = new RecordingEmbeddingClient(4);
        var store = new RecordingVectorStore(4);
        var kb    = new DefaultVectorIndex("docs", "", emb, store, new SlidingChunker());

        var result = kb.index("", Map.of());

        assertEquals(0, result.chunkCount());
        assertTrue(store.upserts.isEmpty());
        assertTrue(emb.calls.isEmpty());
    }

    @Test
    void retrieveEmbedsQueryAndSearches() {

        var emb   = new RecordingEmbeddingClient(4);
        var store = new RecordingVectorStore(4);
        store.stubHits(List.of(new VectorHit("a", 0.99f, "match", Map.of())));

        var kb   = new DefaultVectorIndex("docs", "", emb, store, new SlidingChunker());
        var hits = kb.retrieve("query", 3);

        assertEquals(1,  hits.size());
        assertEquals("a", hits.getFirst().id());
        assertEquals(1,  store.searches.size());
        assertEquals(3,  store.searches.getFirst().k());
    }

    @Test
    void retrieveDefaultsKWhenNonPositive() {

        var emb   = new RecordingEmbeddingClient(4);
        var store = new RecordingVectorStore(4);
        var kb    = new DefaultVectorIndex("docs", "", emb, store, new SlidingChunker());

        kb.retrieve("query", 0);
        kb.retrieve("query", -3);

        assertEquals(5, store.searches.get(0).k());
        assertEquals(5, store.searches.get(1).k());
    }

    @Test
    void dimensionMismatchThrowsAtConstruction() {

        var emb   = new RecordingEmbeddingClient(8);
        var store = new RecordingVectorStore(16);

        var ex = assertThrows(IllegalArgumentException.class,
                () -> new DefaultVectorIndex("docs", "", emb, store, new SlidingChunker()));
        assertTrue(ex.getMessage().contains("dimensions"));
    }

    @Test
    void blankNameThrows() {

        var emb   = new RecordingEmbeddingClient(4);
        var store = new RecordingVectorStore(4);

        assertThrows(IllegalArgumentException.class,
                () -> new DefaultVectorIndex("", "", emb, store, new SlidingChunker()));
        assertThrows(IllegalArgumentException.class,
                () -> new DefaultVectorIndex(null, "", emb, store, new SlidingChunker()));
    }

    @Test
    void chunkMetadataMergesWithIndexMetadata() {

        var emb     = new RecordingEmbeddingClient(4);
        var store   = new RecordingVectorStore(4);
        Chunker chunker = text -> List.of(new Chunk(text, Map.of("chunk-key", "chunk-value")));

        var kb = new DefaultVectorIndex("docs", "", emb, store, chunker);
        kb.index("hello", Map.of("doc-key", "doc-value"));

        var rec = store.upserts.getFirst();
        assertEquals("doc-value",   rec.metadata().get("doc-key"));
        assertEquals("chunk-value", rec.metadata().get("chunk-key"));
    }

    @Test
    void customIdGeneratorIsUsed() {

        var emb     = new RecordingEmbeddingClient(4);
        var store   = new RecordingVectorStore(4);
        var counter = new AtomicInteger();
        var kb      = new DefaultVectorIndex(
                "docs", "", emb, store, new SlidingChunker(10, 0),
                () -> "id-" + counter.incrementAndGet());

        kb.index("abcdefghijklmnop", Map.of());

        assertEquals(2, store.upserts.size());
        assertEquals("id-1", store.upserts.get(0).id());
        assertEquals("id-2", store.upserts.get(1).id());
    }

    @Test
    void embeddingCountMismatchThrows() {

        var emb = new EmbeddingClientReturningWrongCount();
        var store = new RecordingVectorStore(2);
        var kb    = new DefaultVectorIndex(
                "docs", "", emb, store, new SlidingChunker(5, 0));

        var ex = assertThrows(IllegalStateException.class,
                () -> kb.index("abcdefghij", Map.of()));
        assertTrue(ex.getMessage().contains("vectors"));
    }

    private static final class EmbeddingClientReturningWrongCount
            implements ai.agentican.framework.embeddings.EmbeddingClient {

        @Override public List<float[]> embed(List<String> texts) {

            return List.of(new float[]{1f, 2f});
        }

        @Override public int    dimensions() { return 2; }

        @Override public String modelId()    { return "broken"; }
    }
}
