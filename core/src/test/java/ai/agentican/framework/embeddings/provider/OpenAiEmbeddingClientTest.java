package ai.agentican.framework.embeddings.provider;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenAiEmbeddingClientTest {

    @Test
    void apiKeyIsRequired() {

        assertThrows(IllegalArgumentException.class,
                () -> OpenAiEmbeddingClient.builder().build());

        assertThrows(IllegalArgumentException.class,
                () -> OpenAiEmbeddingClient.builder().apiKey("").build());
    }

    @Test
    void unknownModelWithoutDimensionsThrows() {

        assertThrows(IllegalArgumentException.class, () -> OpenAiEmbeddingClient.builder()
                .apiKey("sk-fake")
                .model("custom-model-xyz")
                .build());
    }

    @Test
    void unknownModelWithDimensionsBuilds() {

        var client = OpenAiEmbeddingClient.builder()
                .apiKey("sk-fake")
                .model("custom-model-xyz")
                .dimensions(512)
                .build();

        assertEquals(512, client.dimensions());
        assertEquals("openai:custom-model-xyz", client.modelId());
    }

    @Test
    void nativeDimensionsForKnownModels() {

        assertEquals(1536, OpenAiEmbeddingClient.builder().apiKey("k").model("text-embedding-3-small").build().dimensions());
        assertEquals(3072, OpenAiEmbeddingClient.builder().apiKey("k").model("text-embedding-3-large").build().dimensions());
        assertEquals(1536, OpenAiEmbeddingClient.builder().apiKey("k").model("text-embedding-ada-002").build().dimensions());
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
    void liveSmokeTest() {

        var client = OpenAiEmbeddingClient.builder()
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .model("text-embedding-3-small")
                .build();

        var vectors = client.embed(List.of("hello world", "the quick brown fox"));

        assertEquals(2,      vectors.size());
        assertEquals(1536,   vectors.get(0).length);
        assertEquals(1536,   vectors.get(1).length);

        var same = true;
        for (var i = 0; i < vectors.get(0).length; i++) {
            if (vectors.get(0)[i] != vectors.get(1)[i]) { same = false; break; }
        }
        assertTrue(!same, "Different inputs should produce different vectors");
        assertNotEquals(0f, vectors.get(0)[0]);
    }

    @Test
    void emptyInputReturnsEmptyList() {

        var client = OpenAiEmbeddingClient.builder()
                .apiKey("sk-fake")
                .model("text-embedding-3-small")
                .build();

        assertTrue(client.embed(List.of()).isEmpty());
    }
}
