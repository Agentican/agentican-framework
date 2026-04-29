package ai.agentican.framework.embeddings.provider;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VoyageEmbeddingClientTest {

    @Test
    void apiKeyIsRequired() {

        assertThrows(IllegalArgumentException.class,
                () -> VoyageEmbeddingClient.builder().build());

        assertThrows(IllegalArgumentException.class,
                () -> VoyageEmbeddingClient.builder().apiKey("").build());
    }

    @Test
    void unknownModelWithoutDimensionsThrows() {

        assertThrows(IllegalArgumentException.class, () -> VoyageEmbeddingClient.builder()
                .apiKey("vk-fake")
                .model("custom-voyage-x")
                .build());
    }

    @Test
    void unknownModelWithDimensionsBuilds() {

        var client = VoyageEmbeddingClient.builder()
                .apiKey("vk-fake")
                .model("custom-voyage-x")
                .dimensions(768)
                .build();

        assertEquals(768, client.dimensions());
        assertEquals("voyage:custom-voyage-x", client.modelId());
    }

    @Test
    void nativeDimensionsForKnownModels() {

        assertEquals(1024, VoyageEmbeddingClient.builder().apiKey("k").model("voyage-3").build().dimensions());
        assertEquals(1024, VoyageEmbeddingClient.builder().apiKey("k").model("voyage-3-large").build().dimensions());
        assertEquals( 512, VoyageEmbeddingClient.builder().apiKey("k").model("voyage-3-lite").build().dimensions());
        assertEquals(1024, VoyageEmbeddingClient.builder().apiKey("k").model("voyage-code-3").build().dimensions());
    }

    @Test
    void emptyInputReturnsEmptyList() {

        var client = VoyageEmbeddingClient.builder()
                .apiKey("vk-fake")
                .model("voyage-3")
                .build();

        assertTrue(client.embed(List.of()).isEmpty());
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "VOYAGE_API_KEY", matches = ".+")
    void liveSmokeTest() {

        var client = VoyageEmbeddingClient.builder()
                .apiKey(System.getenv("VOYAGE_API_KEY"))
                .model("voyage-3")
                .build();

        var vectors = client.embed(List.of("hello world", "the quick brown fox"));

        assertEquals(2,    vectors.size());
        assertEquals(1024, vectors.get(0).length);
        assertEquals(1024, vectors.get(1).length);

        var same = true;
        for (var i = 0; i < vectors.get(0).length; i++) {
            if (vectors.get(0)[i] != vectors.get(1)[i]) { same = false; break; }
        }
        assertTrue(!same, "Different inputs should produce different vectors");
        assertNotEquals(0f, vectors.get(0)[0]);
    }
}
