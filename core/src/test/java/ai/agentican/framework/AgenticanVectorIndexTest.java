package ai.agentican.framework;

import ai.agentican.framework.config.LlmConfig;
import ai.agentican.framework.embeddings.RecordingEmbeddingClient;
import ai.agentican.framework.vector.DefaultVectorIndex;
import ai.agentican.framework.vector.VectorIndex;
import ai.agentican.framework.vector.SlidingChunker;
import ai.agentican.framework.vector.code.RetrieveCodeStep;
import ai.agentican.framework.tools.vector.RetrievalToolkit;
import ai.agentican.framework.tools.Toolkit;
import ai.agentican.framework.vector.RecordingVectorStore;
import org.junit.jupiter.api.Test;

import static ai.agentican.framework.MockLlmClient.endTurn;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgenticanVectorIndexTest {

    private static VectorIndex docsKb() {

        var emb   = new RecordingEmbeddingClient(8);
        var store = new RecordingVectorStore(8);
        return new DefaultVectorIndex("docs", "Product documentation", emb, store, new SlidingChunker());
    }

    @Test
    void builderRegistersRetrievalToolkitAndCodeStep() {

        try (var agentican = Agentican.builder()

                .configuration().api()
                    .llm(LlmConfig.builder().apiKey("mock").build())
                    .end()
                .llm("default", request -> endTurn("ok"))
                .vectorIndex(docsKb())
                .build()) {

            var toolkit = agentican.registry().toolkits().get(RetrievalToolkit.SLUG);
            assertNotNull(toolkit, "retrieval toolkit should be auto-registered");
            assertInstanceOf(RetrievalToolkit.class, toolkit);

            var toolNames = toolkit.tools().stream().map(t -> t.name()).toList();
            assertTrue(toolNames.contains("search_docs"),
                    "search_docs tool must be present: " + toolNames);

            var kb = agentican.registry().indexes().get("docs");
            assertNotNull(kb, "docs KB must be in registry");
            assertEquals("docs", kb.name());
        }
    }

    @Test
    void noVectorIndexsMeansNoAutoRegistration() {

        try (var agentican = Agentican.builder()

                .configuration().api()
                    .llm(LlmConfig.builder().apiKey("mock").build())
                    .end()
                .llm("default", request -> endTurn("ok"))
                .build()) {

            assertNull(agentican.registry().toolkits().get(RetrievalToolkit.SLUG));
            assertEquals(0, agentican.registry().indexes().size());
        }
    }

    @Test
    void duplicateVectorIndexNameThrowsAtBuilderRegistration() {

        var first  = docsKb();
        var second = docsKb();

        var builder = Agentican.builder()
                .configuration().api()
                    .llm(LlmConfig.builder().apiKey("mock").build())
                    .end()
                .llm("default", request -> endTurn("ok"))
                .vectorIndex(first);

        assertThrows(IllegalStateException.class, () -> builder.vectorIndex(second));
    }

    @Test
    void userToolkitCollidingWithRetrievalSlugThrowsAtBuild() {

        var builder = Agentican.builder()

                .configuration().api()
                    .llm(LlmConfig.builder().apiKey("mock").build())
                    .end()
                .llm("default", request -> endTurn("ok"))
                .toolkit(RetrievalToolkit.SLUG, new Toolkit() {
                    @Override public java.util.List<ai.agentican.framework.tools.Tool> tools()
                            { return java.util.List.of(); }
                    @Override public boolean handles(String name) { return false; }
                    @Override public String execute(String n, java.util.Map<String, Object> a) { return ""; }
                })
                .vectorIndex(docsKb());

        assertThrows(IllegalStateException.class, builder::build);
    }

    @Test
    void multipleVectorIndexsGenerateMultipleTools() {

        var emb     = new RecordingEmbeddingClient(8);
        var docsKb    = new DefaultVectorIndex("docs",    "docs",    emb, new RecordingVectorStore(8), new SlidingChunker());
        var supportKb = new DefaultVectorIndex("support", "support", emb, new RecordingVectorStore(8), new SlidingChunker());

        try (var agentican = Agentican.builder()

                .configuration().api()
                    .llm(LlmConfig.builder().apiKey("mock").build())
                    .end()
                .llm("default", request -> endTurn("ok"))
                .vectorIndex(docsKb)
                .vectorIndex(supportKb)
                .build()) {

            var toolkit  = agentican.registry().toolkits().get(RetrievalToolkit.SLUG);
            var toolNames = toolkit.tools().stream().map(t -> t.name()).toList();

            assertEquals(2, toolNames.size());
            assertTrue(toolNames.contains("search_docs"));
            assertTrue(toolNames.contains("search_support"));
        }
    }

    @Test
    void retrieveCodeStepSlugReservedWhenKbConfigured() {

        var builder = Agentican.builder()

                .configuration().api()
                    .llm(LlmConfig.builder().apiKey("mock").build())
                    .end()
                .llm("default", request -> endTurn("ok"))
                .codeStep(RetrieveCodeStep.SLUG, Void.class, Void.class, (input, ctx) -> null)
                .vectorIndex(docsKb());

        assertThrows(IllegalStateException.class, builder::build);
    }
}
