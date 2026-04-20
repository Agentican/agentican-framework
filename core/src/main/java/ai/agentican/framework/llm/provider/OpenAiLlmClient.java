package ai.agentican.framework.llm.provider;

import ai.agentican.framework.config.LlmConfig;
import ai.agentican.framework.llm.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.core.JsonValue;
import com.openai.models.responses.FunctionTool;
import com.openai.models.responses.Response;
import com.openai.models.responses.ResponseCreateParams;
import com.openai.models.responses.ResponseFormatTextConfig;
import com.openai.models.responses.ResponseFormatTextJsonSchemaConfig;
import com.openai.models.responses.ResponseStatus;
import com.openai.models.responses.ResponseTextConfig;
import com.openai.models.responses.Tool;
import com.openai.models.responses.WebSearchTool;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

public class OpenAiLlmClient {

    private static final Logger LOG = LoggerFactory.getLogger(OpenAiLlmClient.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private static final Map<String, String> BASE_URLS = new HashMap<>();
    static {
        BASE_URLS.put("openai", null);
        BASE_URLS.put("groq", "https://api.groq.com/openai/v1");
    }

    private static final String GROQ_GPT_OSS_PREFIX = "openai/gpt-oss-";

    private final String provider;
    private final String model;
    private final long maxTokens;
    private final Double temperature;

    private final OpenAIClient client;

    private OpenAiLlmClient(LlmConfig config, OpenAIClient client) {

        this.provider = config.provider();
        this.model = config.model();
        this.maxTokens = config.maxTokens();
        this.temperature = config.temperature();
        this.client = client;
    }

    public static LlmClient create(LlmConfig config) {

        if (config == null)
            throw new IllegalStateException("LLM configuration is required");

        if (!BASE_URLS.containsKey(config.provider()))
            throw new IllegalStateException(
                    "Unsupported Responses-API provider: " + config.provider()
                    + " (expected one of " + BASE_URLS.keySet() + ")");

        var clientBuilder = OpenAIOkHttpClient.builder().apiKey(config.apiKey());

        var baseUrl = BASE_URLS.get(config.provider());
        if (baseUrl != null) clientBuilder.baseUrl(baseUrl);

        var llmClient = new OpenAiLlmClient(config, clientBuilder.build());

        return LlmClient.withLogging(llmClient::send);
    }

    private LlmResponse send(LlmRequest request) {

        var userText = buildUserText(request);

        var paramsBuilder = ResponseCreateParams.builder()
                .model(model)
                .instructions(request.systemPrompt())
                .input(userText)
                .maxOutputTokens(maxTokens);

        if (temperature != null) paramsBuilder.temperature(temperature);

        if (nativeStructuredOutputSupported(request))
            paramsBuilder.text(buildTextConfig(request.structuredOutput()));

        if (request.tools() != null) {

            request.tools().forEach(tool -> {

                var parameters = FunctionTool.Parameters.builder()
                        .putAdditionalProperty("type", JsonValue.from("object"))
                        .putAdditionalProperty("properties", JsonValue.from(tool.properties()))
                        .putAdditionalProperty("required", JsonValue.from(tool.required()))
                        .build();

                paramsBuilder.addTool(FunctionTool.builder()
                        .name(tool.name())
                        .description(tool.description())
                        .parameters(parameters)
                        .strict(false)
                        .build());
            });
        }

        addBuiltInSearchTool(paramsBuilder);

        var response = client.responses().create(paramsBuilder.build());

        return translate(response);
    }

    /**
     * OpenAI Responses API supports native {@code response_format: json_schema} on
     * the {@code openai} provider. Groq's Responses adapter does not yet honor it
     * reliably, so we keep prompt-steering for groq.
     */
    private boolean nativeStructuredOutputSupported(LlmRequest request) {

        return request.structuredOutput() != null && "openai".equals(provider);
    }

    private static ResponseTextConfig buildTextConfig(StructuredOutput so) {

        var schemaBuilder = ResponseFormatTextJsonSchemaConfig.Schema.builder();

        var fields = MAPPER.<Map<String, Object>>convertValue(so.schema(),
                new TypeReference<Map<String, Object>>() {});
        fields.forEach((k, v) -> schemaBuilder.putAdditionalProperty(k, JsonValue.from(v)));

        var jsonSchema = ResponseFormatTextJsonSchemaConfig.builder()
                .name(so.name())
                .schema(schemaBuilder.build())
                .strict(so.strict())
                .build();

        return ResponseTextConfig.builder()
                .format(ResponseFormatTextConfig.ofJsonSchema(jsonSchema))
                .build();
    }

    private void addBuiltInSearchTool(ResponseCreateParams.Builder paramsBuilder) {

        switch (provider) {
            case "openai" -> paramsBuilder.addTool(
                    WebSearchTool.builder().type(WebSearchTool.Type.WEB_SEARCH).build());

            case "groq" -> {
                if (model.startsWith(GROQ_GPT_OSS_PREFIX)) {
                    paramsBuilder.addTool(GROQ_BROWSER_SEARCH_TOOL);
                }
            }
        }
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

    private static LlmResponse translate(Response response) {

        var textBuilder = new StringBuilder();
        var toolCalls = new ArrayList<ToolCall>();
        long webSearchRequests = 0;

        for (var item : response.output()) {

            if (item.isMessage()) {

                for (var content : item.asMessage().content()) {

                    if (content.isOutputText())
                        textBuilder.append(content.asOutputText().text());
                }
            } else if (item.isFunctionCall()) {

                var call = item.asFunctionCall();
                var args = parseArgs(call.arguments());

                toolCalls.add(new ToolCall(call.callId(), call.name(), args));

            } else if (item.isWebSearchCall()) {

                webSearchRequests++;
            }
        }

        var stopReason = resolveStopReason(response, !toolCalls.isEmpty());

        var usage = response.usage();

        long inputTotal = usage.map(u -> u.inputTokens()).orElse(0L);
        long cacheReadTokens = usage
                .map(u -> u.inputTokensDetails().cachedTokens())
                .orElse(0L);
        long inputTokens = Math.max(0, inputTotal - cacheReadTokens);
        long outputTokens = usage.map(u -> u.outputTokens()).orElse(0L);

        return new LlmResponse(
                textBuilder.toString(),
                toolCalls,
                stopReason,
                inputTokens,
                outputTokens,
                cacheReadTokens,
                0L,
                webSearchRequests);
    }

    private static StopReason resolveStopReason(Response response, boolean hasToolCalls) {

        if (hasToolCalls) return StopReason.TOOL_USE;

        if (response.status().map(s -> s.equals(ResponseStatus.INCOMPLETE)).orElse(false)) {

            var reason = response.incompleteDetails()
                    .flatMap(d -> d.reason())
                    .map(Object::toString)
                    .orElse("");

            if (reason.contains("max_output_tokens")) return StopReason.MAX_TOKENS;

            LOG.warn("OpenAI response was incomplete (reason={}), mapping to END_TURN", reason);
        }

        return StopReason.END_TURN;
    }

    private static Map<String, Object> parseArgs(String arguments) {

        if (arguments == null || arguments.isBlank()) return Map.of();

        try {

            var parsed = MAPPER.readValue(arguments, MAP_TYPE);
            return parsed != null ? parsed : Map.of();

        } catch (Exception e) {

            LOG.warn("Failed to parse OpenAI tool-call arguments as JSON: {}", arguments, e);
            return Map.of();
        }
    }

    private static final Tool GROQ_BROWSER_SEARCH_TOOL = buildRawTool("browser_search");

    // Groq's Responses API supports a built-in tool named `browser_search`,
    // but the OpenAI SDK has no typed variant for it (OpenAI's own API uses
    // `web_search`). Constructing a Tool with raw JSON via reflection is the
    // least-bad option — it's bounded to a single static initializer, and
    // produces the same wire shape as the SDK's `Tool.ofLocalShell()` trick
    // for its own generic JsonValue slot.
    private static Tool buildRawTool(String typeName) {

        try {

            Constructor<?> ctor = Arrays.stream(Tool.class.getDeclaredConstructors())
                    .max(Comparator.comparingInt(Constructor::getParameterCount))
                    .orElseThrow(() -> new IllegalStateException("no Tool constructor"));
            ctor.setAccessible(true);

            var args = new Object[ctor.getParameterCount()];
            args[args.length - 1] = JsonValue.from(Map.of("type", typeName));

            return (Tool) ctor.newInstance(args);

        } catch (ReflectiveOperationException t) {
            throw new IllegalStateException("Failed to build raw Tool for type=" + typeName, t);
        }
    }
}
