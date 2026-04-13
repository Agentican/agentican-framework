package ai.agentican.framework.knowledge;

import ai.agentican.framework.MockLlmClient;
import ai.agentican.framework.llm.LlmClient;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LlmKnowledgeFactExtractorTest {

    @Test
    void extractsFactsFromText() {

        var json = """
                {
                  "facts": [
                    {"name": "Pricing", "content": "Costs $10/month", "tags": ["pricing/saas"]},
                    {"name": "Launch", "content": "Launched 2024", "tags": ["company/timeline"]}
                  ]
                }
                """;

        var mockLlm = new MockLlmClient().onSend("", json);

        var extractor = new LlmKnowledgeExtractor(mockLlm.toLlmClient());

        var facts = extractor.extractFacts("some text about a product");

        assertEquals(2, facts.size());
        assertEquals("Pricing", facts.get(0).name());
        assertEquals("Costs $10/month", facts.get(0).content());
        assertEquals("Launch", facts.get(1).name());
        assertEquals("Launched 2024", facts.get(1).content());
    }

    @Test
    void emptyTextReturnsEmpty() {

        var extractor = new LlmKnowledgeExtractor(new MockLlmClient().toLlmClient());

        assertTrue(extractor.extractFacts("").isEmpty());
        assertTrue(extractor.extractFacts(null).isEmpty());
    }

    @Test
    void llmFailureReturnsEmpty() {

        LlmClient throwingClient = request -> {

            throw new RuntimeException("LLM is down");
        };

        var extractor = new LlmKnowledgeExtractor(throwingClient);

        assertTrue(extractor.extractFacts("some text").isEmpty());
    }

    @Test
    void invalidJsonReturnsEmpty() {

        var mockLlm = new MockLlmClient().onSend("", "this is not JSON at all");

        var extractor = new LlmKnowledgeExtractor(mockLlm.toLlmClient());

        assertTrue(extractor.extractFacts("some text").isEmpty());
    }
}
