package ai.agentican.framework.knowledge;

import ai.agentican.framework.MockLlmClient;
import ai.agentican.framework.llm.LlmClient;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LlmKnowledgeFactExtractorTest {

    @Test
    void extractsMultipleCreateEntries() {

        var json = """
                {"entries": [
                  {
                    "action": "create",
                    "name": "Claude Opus 4.6",
                    "description": "Anthropic frontier model, Feb 2026.",
                    "facts": [
                      {"name": "GPQA", "content": "Scores 91.3% on GPQA Diamond.", "tags": ["anthropic/claude/opus-4-6/benchmarks"]},
                      {"name": "Pricing", "content": "Input $15 / output $75 per million tokens.", "tags": ["anthropic/claude/opus-4-6/pricing"]}
                    ]
                  },
                  {
                    "action": "create",
                    "name": "Gemini 3.1 Pro",
                    "description": "Google frontier model.",
                    "facts": [
                      {"name": "Pricing", "content": "Input $2 / output $12 per million tokens.", "tags": ["google/gemini/3-1-pro/pricing"]}
                    ]
                  }
                ]}
                """;

        var mockLlm = new MockLlmClient().onSend("", json);

        var extractor = new LlmKnowledgeExtractor(mockLlm.toLlmClient());

        var result = extractor.extract(null, "research text", List.of());

        assertEquals(2, result.entries().size());
        assertEquals(ExtractedEntry.Action.CREATE, result.entries().get(0).action());
        assertEquals("Claude Opus 4.6", result.entries().get(0).name());
        assertEquals(2, result.entries().get(0).facts().size());
        assertEquals("Gemini 3.1 Pro", result.entries().get(1).name());
    }

    @Test
    void parsesUpdateActionAgainstExistingEntry() {

        var json = """
                {"entries": [
                  {
                    "action": "update",
                    "existingEntryId": "existing-123",
                    "facts": [
                      {"name": "New limit", "content": "New benchmark added.", "tags": ["domain/x"]}
                    ]
                  }
                ]}
                """;

        var mockLlm = new MockLlmClient().onSend("", json);

        var extractor = new LlmKnowledgeExtractor(mockLlm.toLlmClient());

        var existing = List.of(new KnowledgeEntrySummary("existing-123", "Topic X", "desc", 5));

        var result = extractor.extract(null, "more findings", existing);

        assertEquals(1, result.entries().size());
        assertEquals(ExtractedEntry.Action.UPDATE, result.entries().getFirst().action());
        assertEquals("existing-123", result.entries().getFirst().existingEntryId());
        assertEquals(1, result.entries().getFirst().facts().size());
    }

    @Test
    void updateWithoutIdIsSkipped() {

        var json = """
                {"entries": [
                  {"action": "update", "facts": [{"name": "x", "content": "y", "tags": []}]}
                ]}
                """;

        var mockLlm = new MockLlmClient().onSend("", json);

        var extractor = new LlmKnowledgeExtractor(mockLlm.toLlmClient());

        var result = extractor.extract(null, "text", List.of());

        assertTrue(result.entries().isEmpty());
    }

    @Test
    void emptyEntriesArrayReturnsEmpty() {

        var mockLlm = new MockLlmClient().onSend("", "{\"entries\": []}");

        var extractor = new LlmKnowledgeExtractor(mockLlm.toLlmClient());

        assertTrue(extractor.extract(null, "anything", List.of()).entries().isEmpty());
    }

    @Test
    void emptyTextReturnsEmpty() {

        var extractor = new LlmKnowledgeExtractor(new MockLlmClient().toLlmClient());

        assertTrue(extractor.extract(null, "", List.of()).entries().isEmpty());
        assertTrue(extractor.extract(null, null, List.of()).entries().isEmpty());
    }

    @Test
    void llmFailureReturnsEmpty() {

        LlmClient throwing = request -> { throw new RuntimeException("down"); };

        var extractor = new LlmKnowledgeExtractor(throwing);

        assertTrue(extractor.extract(null, "some text", List.of()).entries().isEmpty());
    }

    @Test
    void invalidJsonReturnsEmpty() {

        var mockLlm = new MockLlmClient().onSend("", "this is not JSON at all");

        var extractor = new LlmKnowledgeExtractor(mockLlm.toLlmClient());

        assertTrue(extractor.extract(null, "some text", List.of()).entries().isEmpty());
    }

    @Test
    void existingEntriesAreRenderedInPrompt() {

        var json = "{\"entries\": []}";
        var mockLlm = new MockLlmClient().onSend("existing-abc", json);

        var extractor = new LlmKnowledgeExtractor(mockLlm.toLlmClient());

        var existing = List.of(new KnowledgeEntrySummary("existing-abc", "Topic", "Covers X", 3));

        var result = extractor.extract(null, "source text", existing);

        assertNotNull(result);
    }
}
