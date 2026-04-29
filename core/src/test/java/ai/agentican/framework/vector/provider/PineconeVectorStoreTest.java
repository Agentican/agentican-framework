package ai.agentican.framework.vector.provider;

import ai.agentican.framework.vector.VectorRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PineconeVectorStoreTest {

    @Test
    void apiKeyRequiredWhenNoClientInjected() {

        assertThrows(IllegalArgumentException.class, () -> PineconeVectorStore.builder()
                .indexName("test")
                .dimensions(3)
                .build());
    }

    @Test
    void indexNameRequired() {

        assertThrows(IllegalArgumentException.class, () -> PineconeVectorStore.builder()
                .apiKey("pk-fake")
                .dimensions(3)
                .build());
    }

    @Test
    void dimensionsRequired() {

        assertThrows(IllegalArgumentException.class, () -> PineconeVectorStore.builder()
                .apiKey("pk-fake")
                .indexName("test")
                .build());
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "PINECONE_API_KEY",    matches = ".+")
    @EnabledIfEnvironmentVariable(named = "PINECONE_TEST_INDEX", matches = ".+")
    void liveSmokeUpsertSearchDelete() throws Exception {

        var store = PineconeVectorStore.builder()
                .apiKey(System.getenv("PINECONE_API_KEY"))
                .indexName(System.getenv("PINECONE_TEST_INDEX"))
                .namespace("agentican-test-" + UUID.randomUUID().toString().substring(0, 8))
                .dimensions(3)
                .build();

        try {
            var idA = "00000000-0000-0000-0000-00000000000a";
            var idB = "00000000-0000-0000-0000-00000000000b";

            store.upsert(List.of(
                    new VectorRecord(idA, new float[]{1f, 0f, 0f}, "near", Map.of("k", "v")),
                    new VectorRecord(idB, new float[]{0f, 1f, 0f}, "far",  Map.of())));

            Thread.sleep(5_000);

            var hits = store.search(new float[]{1f, 0f, 0f}, 5);

            assertTrue(hits.size() >= 1, "Expected at least one hit, got: " + hits);
            assertEquals(idA, hits.getFirst().id());
            assertEquals("near", hits.getFirst().content());
            assertEquals("v",    hits.getFirst().metadata().get("k"));
        }
        finally {
            try { store.delete(List.of(
                    "00000000-0000-0000-0000-00000000000a",
                    "00000000-0000-0000-0000-00000000000b")); }
            catch (Exception _) {  }
        }
    }
}
