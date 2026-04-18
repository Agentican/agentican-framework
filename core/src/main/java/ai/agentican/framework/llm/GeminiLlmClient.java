package ai.agentican.framework.llm;

import ai.agentican.framework.config.LlmConfig;
import com.google.genai.Client;
import com.google.genai.types.Candidate;
import com.google.genai.types.Content;
import com.google.genai.types.FinishReason;
import com.google.genai.types.FunctionDeclaration;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.GoogleSearch;
import com.google.genai.types.Part;
import com.google.genai.types.Tool;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class GeminiLlmClient {

    private static final Logger LOG = LoggerFactory.getLogger(GeminiLlmClient.class);

    private final String model;
    private final int maxOutputTokens;
    private final Float temperature;

    private final Client client;

    private GeminiLlmClient(LlmConfig config) {

        this.client = Client.builder().apiKey(config.apiKey()).build();
        this.model = config.model();
        this.maxOutputTokens = (int) Math.min(config.maxTokens(), Integer.MAX_VALUE);
        this.temperature = config.temperature() != null ? config.temperature().floatValue() : null;
    }

    private static GeminiLlmClient of(LlmConfig llmConfig) {

        return new GeminiLlmClient(llmConfig);
    }

    public static LlmClient create(LlmConfig config) {

        if (config == null)
            throw new IllegalStateException("LLM configuration is required");

        if (!"gemini".equals(config.provider()))
            throw new IllegalStateException("Unsupported LLM provider: " + config.provider());

        var llmClient = GeminiLlmClient.of(config);

        return LlmClient.withLogging(llmClient::send);
    }

    private LlmResponse send(LlmRequest request) {

        var systemContent = Content.fromParts(Part.fromText(request.systemPrompt()));
        var userText = buildUserText(request);

        var tools = new ArrayList<Tool>();

        if (request.tools() != null && !request.tools().isEmpty()) {

            var declarations = new ArrayList<FunctionDeclaration>();

            request.tools().forEach(tool -> {

                var schema = new HashMap<String, Object>();
                schema.put("type", "object");
                schema.put("properties", tool.properties());
                schema.put("required", tool.required());

                declarations.add(FunctionDeclaration.builder()
                        .name(tool.name())
                        .description(tool.description())
                        .parametersJsonSchema(schema)
                        .build());
            });

            tools.add(Tool.builder().functionDeclarations(declarations).build());
        }

        tools.add(Tool.builder().googleSearch(GoogleSearch.builder().build()).build());

        var configBuilder = GenerateContentConfig.builder()
                .systemInstruction(systemContent)
                .maxOutputTokens(maxOutputTokens)
                .tools(tools);

        if (temperature != null) configBuilder.temperature(temperature);

        var response = client.models.generateContent(model, userText, configBuilder.build());

        return translate(response);
    }

    private static String buildUserText(LlmRequest request) {

        var sb = new StringBuilder();

        if (request.userTask() != null && !request.userTask().isBlank())
            sb.append(request.userTask());

        if (request.userMessage() != null && !request.userMessage().isBlank()) {

            if (!sb.isEmpty()) sb.append("\n\n");

            sb.append(request.userMessage());
        }

        return sb.toString();
    }

    private static LlmResponse translate(GenerateContentResponse response) {

        var textBuilder = new StringBuilder();
        var toolCalls = new ArrayList<ToolCall>();
        long webSearchRequests = 0;

        var candidates = response.candidates().orElse(List.of());

        if (!candidates.isEmpty()) {

            var candidate = candidates.get(0);

            candidate.content().ifPresent(content -> {

                for (var part : content.parts().orElse(List.of())) {

                    part.text().ifPresent(textBuilder::append);

                    part.functionCall().ifPresent(fc -> {

                        var id = fc.id().orElseGet(() ->
                                "call_" + UUID.randomUUID().toString().substring(0, 8));
                        var name = fc.name().orElse("");
                        var args = fc.args().orElse(Map.of());

                        toolCalls.add(ToolCall.of(id, name, args));
                    });
                }
            });

            webSearchRequests = candidate.groundingMetadata()
                    .flatMap(gm -> gm.webSearchQueries())
                    .map(queries -> (long) queries.size())
                    .orElse(0L);
        }

        var stopReason = resolveStopReason(candidates, !toolCalls.isEmpty());

        var usage = response.usageMetadata();

        long promptTotal = usage.flatMap(u -> u.promptTokenCount()).orElse(0).longValue();
        long cacheReadTokens = usage.flatMap(u -> u.cachedContentTokenCount()).orElse(0).longValue();
        long inputTokens = Math.max(0, promptTotal - cacheReadTokens);
        long outputTokens = usage.flatMap(u -> u.candidatesTokenCount()).orElse(0).longValue();

        return LlmResponse.of(
                textBuilder.toString(),
                toolCalls,
                stopReason,
                inputTokens,
                outputTokens,
                cacheReadTokens,
                0L,
                webSearchRequests);
    }

    private static StopReason resolveStopReason(List<Candidate> candidates, boolean hasToolCalls) {

        if (hasToolCalls) return StopReason.TOOL_USE;

        if (candidates.isEmpty()) return StopReason.END_TURN;

        var known = candidates.get(0).finishReason()
                .map(FinishReason::knownEnum)
                .orElse(FinishReason.Known.FINISH_REASON_UNSPECIFIED);

        return switch (known) {
            case MAX_TOKENS -> StopReason.MAX_TOKENS;
            case STOP, FINISH_REASON_UNSPECIFIED -> StopReason.END_TURN;
            default -> {
                LOG.warn("Gemini response stopped with reason {}; mapping to END_TURN", known);
                yield StopReason.END_TURN;
            }
        };
    }
}
