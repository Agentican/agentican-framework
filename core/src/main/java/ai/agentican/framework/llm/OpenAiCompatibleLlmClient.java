package ai.agentican.framework.llm;

import ai.agentican.framework.config.LlmConfig;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.core.JsonValue;
import com.openai.models.FunctionDefinition;
import com.openai.models.FunctionParameters;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import com.openai.models.chat.completions.ChatCompletionFunctionTool;
import com.openai.models.chat.completions.ChatCompletionSystemMessageParam;
import com.openai.models.chat.completions.ChatCompletionTool;
import com.openai.models.chat.completions.ChatCompletionUserMessageParam;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * One client for every provider that speaks the OpenAI Chat Completions wire format.
 * Covers hosted providers with a registered base URL (SambaNova, Together, Fireworks)
 * and the {@code openai-compatible} escape hatch for self-hosted endpoints
 * (Ollama, vLLM, LiteLLM, LocalAI, custom proxies) where the user supplies
 * {@link LlmConfig#baseUrl()} directly.
 */
public class OpenAiCompatibleLlmClient {

    private static final Logger LOG = LoggerFactory.getLogger(OpenAiCompatibleLlmClient.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private static final String OPENAI_COMPATIBLE = "openai-compatible";

    private static final Map<String, String> BASE_URLS = new HashMap<>();
    static {
        BASE_URLS.put("sambanova", "https://api.sambanova.ai/v1");
        BASE_URLS.put("together",  "https://api.together.xyz/v1");
        BASE_URLS.put("fireworks", "https://api.fireworks.ai/inference/v1");
    }

    private final String provider;
    private final String model;
    private final long maxTokens;
    private final Double temperature;

    private final OpenAIClient client;

    private OpenAiCompatibleLlmClient(LlmConfig config, String baseUrl) {

        this.provider = config.provider();
        this.model = config.model();
        this.maxTokens = config.maxTokens();
        this.temperature = config.temperature();

        this.client = OpenAIOkHttpClient.builder()
                .baseUrl(baseUrl)
                .apiKey(config.apiKey())
                .build();
    }

    public static LlmClient create(LlmConfig config) {

        if (config == null)
            throw new IllegalStateException("LLM configuration is required");

        var baseUrl = resolveBaseUrl(config);

        var llmClient = new OpenAiCompatibleLlmClient(config, baseUrl);

        return LlmClient.withLogging(llmClient::send);
    }

    private static String resolveBaseUrl(LlmConfig config) {

        if (OPENAI_COMPATIBLE.equals(config.provider())) {

            var url = config.baseUrl();

            if (url == null || url.isBlank())
                throw new IllegalStateException(
                        "baseUrl is required when provider is '" + OPENAI_COMPATIBLE + "'");

            return url;
        }

        var url = BASE_URLS.get(config.provider());

        if (url == null)
            throw new IllegalStateException(
                    "Unsupported OpenAI-compatible provider: " + config.provider()
                    + " (expected one of " + BASE_URLS.keySet() + " or '" + OPENAI_COMPATIBLE + "')");

        return url;
    }

    private LlmResponse send(LlmRequest request) {

        var paramsBuilder = ChatCompletionCreateParams.builder()
                .model(model)
                .maxCompletionTokens(maxTokens)
                .addMessage(ChatCompletionSystemMessageParam.builder()
                        .content(request.systemPrompt())
                        .build())
                .addMessage(ChatCompletionUserMessageParam.builder()
                        .content(buildUserText(request))
                        .build());

        if (temperature != null) paramsBuilder.temperature(temperature);

        if (request.tools() != null) {

            request.tools().forEach(tool -> {

                var parameters = FunctionParameters.builder()
                        .putAdditionalProperty("type", JsonValue.from("object"))
                        .putAdditionalProperty("properties", JsonValue.from(tool.properties()))
                        .putAdditionalProperty("required", JsonValue.from(tool.required()))
                        .build();

                var functionTool = ChatCompletionFunctionTool.builder()
                        .function(FunctionDefinition.builder()
                                .name(tool.name())
                                .description(tool.description())
                                .parameters(parameters)
                                .strict(false)
                                .build())
                        .build();

                paramsBuilder.addTool(ChatCompletionTool.ofFunction(functionTool));
            });
        }

        var completion = client.chat().completions().create(paramsBuilder.build());

        return translate(completion);
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

    private LlmResponse translate(ChatCompletion completion) {

        if (completion.choices().isEmpty())
            return LlmResponse.of("", List.of(), StopReason.END_TURN, 0, 0, 0, 0, 0);

        var choice = completion.choices().get(0);
        var message = choice.message();

        var text = message.content().orElse("");

        var toolCalls = new ArrayList<ToolCall>();

        message.toolCalls().ifPresent(calls -> calls.forEach(call -> {

            if (!call.isFunction()) return;

            var fn = call.asFunction();
            var args = parseArgs(fn.function().arguments());

            toolCalls.add(ToolCall.of(fn.id(), fn.function().name(), args));
        }));

        var stopReason = resolveStopReason(choice, !toolCalls.isEmpty());

        var usage = completion.usage();

        long promptTotal = usage.map(u -> u.promptTokens()).orElse(0L);
        long cacheReadTokens = usage
                .flatMap(u -> u.promptTokensDetails())
                .flatMap(d -> d.cachedTokens())
                .orElse(0L);
        long inputTokens = Math.max(0, promptTotal - cacheReadTokens);
        long outputTokens = usage.map(u -> u.completionTokens()).orElse(0L);

        return LlmResponse.of(text, toolCalls, stopReason,
                inputTokens, outputTokens, cacheReadTokens, 0L, 0L);
    }

    private StopReason resolveStopReason(ChatCompletion.Choice choice, boolean hasToolCalls) {

        if (hasToolCalls) return StopReason.TOOL_USE;

        var reason = choice.finishReason().asString();

        return switch (reason) {
            case "length" -> StopReason.MAX_TOKENS;
            case "stop" -> StopReason.END_TURN;
            case "tool_calls" -> StopReason.TOOL_USE;
            default -> {
                LOG.warn("{} response finished with reason '{}'; mapping to END_TURN", provider, reason);
                yield StopReason.END_TURN;
            }
        };
    }

    private Map<String, Object> parseArgs(String arguments) {

        if (arguments == null || arguments.isBlank()) return Map.of();

        try {

            var parsed = MAPPER.readValue(arguments, MAP_TYPE);
            return parsed != null ? parsed : Map.of();

        } catch (Exception e) {

            LOG.warn("Failed to parse {} tool-call arguments as JSON: {}", provider, arguments, e);
            return Map.of();
        }
    }
}
