package ai.agentican.framework.knowledge;

import ai.agentican.framework.llm.LlmClient;
import ai.agentican.framework.llm.LlmRequest;
import ai.agentican.framework.tools.ToolDefinition;
import ai.agentican.framework.util.Json;
import ai.agentican.framework.util.Templates;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class LlmKnowledgeExtractor implements KnowledgeExtractor {

    private static final Logger LOG = LoggerFactory.getLogger(LlmKnowledgeExtractor.class);
    private static final Templates TEMPLATES = new Templates();

    private final LlmClient llm;

    public LlmKnowledgeExtractor(LlmClient llm) {

        if (llm == null)
            throw new IllegalArgumentException("LLM client is required");

        this.llm = llm;
    }

    @Override
    public List<KnowledgeFact> extractFacts(String text) {

        if (text == null || text.isBlank())
            return List.of();

        try {

            var systemPrompt = TEMPLATES.factExtractionPrompt();
            var tools = List.<ToolDefinition>of();
            var iteration = 0;

            var llmResponse = llm.send(new LlmRequest(systemPrompt, text, tools, iteration));

            var llmResposneText = llmResponse.text();

            var extractionOutput = Json.findObject(llmResposneText, ExtractionOutput.class);

            if (extractionOutput.facts == null || extractionOutput.facts.isEmpty())
                return List.of();

            return extractionOutput.facts.stream()
                    .filter(fact -> fact.name != null && !fact.name.isBlank())
                    .map(fact -> KnowledgeFact.of(fact.name, fact.content != null ? fact.content : "",
                            fact.tags != null ? fact.tags : List.of()))
                    .toList();
        }
        catch (Exception e) {

            LOG.warn("Fact extraction failed: {}", e.getMessage());
            return List.of();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ExtractionOutput(List<ExtractedFact> facts) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ExtractedFact(String name, String content, List<String> tags) {}
}
