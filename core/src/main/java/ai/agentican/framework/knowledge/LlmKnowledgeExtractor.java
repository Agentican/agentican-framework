package ai.agentican.framework.knowledge;

import ai.agentican.framework.llm.LlmClient;
import ai.agentican.framework.llm.LlmRequest;
import ai.agentican.framework.tools.ToolDefinition;
import ai.agentican.framework.util.Json;
import ai.agentican.framework.util.Templates;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
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
    public List<ExtractedEntry> extract(String input, String output, List<KnowledgeEntry> existingEntries) {

        if (output == null || output.isBlank())
            return List.<ExtractedEntry>of();

        try {

            var systemPrompt = TEMPLATES.renderFactExtractionPrompt(existingEntries);
            var tools = List.<ToolDefinition>of();

            var userMessage = new StringBuilder();

            if (input != null && !input.isBlank()) {
                userMessage.append("<agent-input>\n").append(input).append("\n</agent-input>\n\n");
            }

            userMessage.append("<agent-output>\n").append(output).append("\n</agent-output>\n\n");
            userMessage.append("Extract knowledge from <agent-output>, per the rules. "
                    + "Only extract facts the agent DISCOVERED — skip any fact that already appears in <agent-input>.");

            var llmResponse = llm.send(new LlmRequest(systemPrompt, null, userMessage.toString(), tools, 0, null, null, null));

            var parsed = Json.findObject(llmResponse.text(), ExtractionOutput.class);

            if (parsed == null || parsed.entries == null || parsed.entries.isEmpty())
                return List.<ExtractedEntry>of();

            var entries = new ArrayList<ExtractedEntry>();

            for (var rawEntry : parsed.entries) {

                var action = ExtractedEntry.Action.parse(rawEntry.action);

                if (action == ExtractedEntry.Action.UPDATE
                        && (rawEntry.existingEntryId == null || rawEntry.existingEntryId.isBlank())) {
                    LOG.debug("Skipping UPDATE entry with no existingEntryId");
                    continue;
                }

                var facts = rawEntry.facts == null
                        ? List.<KnowledgeFact>of()
                        : rawEntry.facts.stream()
                                .filter(f -> f.name != null && !f.name.isBlank())
                                .map(f -> KnowledgeFact.of(
                                        f.name,
                                        f.content != null ? f.content : "",
                                        f.tags != null ? f.tags : List.of()))
                                .toList();

                if (facts.isEmpty() && action == ExtractedEntry.Action.UPDATE) continue;
                if (facts.isEmpty() && (rawEntry.name == null || rawEntry.name.isBlank())) continue;

                entries.add(new ExtractedEntry(
                        action,
                        rawEntry.existingEntryId,
                        rawEntry.name,
                        rawEntry.description,
                        facts));
            }

            return entries;
        }
        catch (Exception e) {

            LOG.warn("Fact extraction failed: {}", e.getMessage());
            return List.<ExtractedEntry>of();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ExtractionOutput(List<RawEntry> entries) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record RawEntry(
            String action,
            String existingEntryId,
            String name,
            String description,
            List<RawFact> facts) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record RawFact(String name, String content, List<String> tags) {}
}
