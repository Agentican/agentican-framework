package ai.agentican.framework.vector.code;

import ai.agentican.framework.embeddings.RecordingEmbeddingClient;
import ai.agentican.framework.vector.DefaultVectorIndex;
import ai.agentican.framework.vector.VectorIndexRegistry;
import ai.agentican.framework.vector.SlidingChunker;
import ai.agentican.framework.vector.RecordingVectorStore;
import ai.agentican.framework.vector.VectorHit;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RetrieveCodeStepTest {

    @Test
    void executeReturnsHitsAndFormatted() {

        var emb   = new RecordingEmbeddingClient(4);
        var store = new RecordingVectorStore(4);
        store.stubHits(List.of(
                new VectorHit("a", 0.95f, "first chunk", Map.of("src", "doc1")),
                new VectorHit("b", 0.80f, "second chunk", Map.of("src", "doc2"))));

        var kb = new DefaultVectorIndex("docs", "test", emb, store, new SlidingChunker());
        var registry = new VectorIndexRegistry();
        registry.register(kb);

        var step   = new RetrieveCodeStep(registry);
        var output = step.execute(new RetrieveQuery("docs", "any query", 5), null);

        assertEquals(2, output.hits().size());
        assertEquals("a",            output.hits().get(0).id());
        assertEquals("first chunk",  output.hits().get(0).content());
        assertEquals("doc1",         output.hits().get(0).metadata().get("src"));
        assertEquals(0.95,           output.hits().get(0).score(), 1e-6);

        assertEquals("first chunk" + RetrieveCodeStep.HIT_SEPARATOR + "second chunk",
                     output.formatted());
    }

    @Test
    void unknownVectorIndexThrows() {

        var registry = new VectorIndexRegistry();
        var step     = new RetrieveCodeStep(registry);

        var ex = assertThrows(IllegalStateException.class,
                () -> step.execute(new RetrieveQuery("missing", "q", 1), null));

        assertTrue(ex.getMessage().contains("missing"));
    }

    @Test
    void emptyHitsProduceEmptyFormatted() {

        var emb   = new RecordingEmbeddingClient(4);
        var store = new RecordingVectorStore(4);

        var kb = new DefaultVectorIndex("docs", "test", emb, store, new SlidingChunker());
        var registry = new VectorIndexRegistry();
        registry.register(kb);

        var output = new RetrieveCodeStep(registry).execute(
                new RetrieveQuery("docs", "q", 5), null);

        assertTrue(output.hits().isEmpty());
        assertEquals("", output.formatted());
    }

    @Test
    void registryRequired() {

        assertThrows(IllegalArgumentException.class, () -> new RetrieveCodeStep(null));
    }

    @Test
    void retrieveQueryDefaultsKWhenNonPositive() {

        var q = new RetrieveQuery("docs", "any", 0);
        assertEquals(5, q.k());

        var q2 = new RetrieveQuery("docs", "any", -3);
        assertEquals(5, q2.k());
    }

    @Test
    void retrieveQueryRequiresVectorIndexName() {

        assertThrows(IllegalArgumentException.class, () -> new RetrieveQuery("",  "q", 1));
        assertThrows(IllegalArgumentException.class, () -> new RetrieveQuery(null, "q", 1));
    }
}
