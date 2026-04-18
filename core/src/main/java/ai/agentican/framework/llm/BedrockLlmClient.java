package ai.agentican.framework.llm;

import ai.agentican.framework.config.LlmConfig;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.document.Document;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ConversationRole;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseRequest;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseResponse;
import software.amazon.awssdk.services.bedrockruntime.model.InferenceConfiguration;
import software.amazon.awssdk.services.bedrockruntime.model.Message;
import software.amazon.awssdk.services.bedrockruntime.model.StopReason;
import software.amazon.awssdk.services.bedrockruntime.model.SystemContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.Tool;
import software.amazon.awssdk.services.bedrockruntime.model.ToolConfiguration;
import software.amazon.awssdk.services.bedrockruntime.model.ToolInputSchema;
import software.amazon.awssdk.services.bedrockruntime.model.ToolSpecification;
import software.amazon.awssdk.services.bedrockruntime.model.ToolUseBlock;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class BedrockLlmClient {

    private static final Logger LOG = LoggerFactory.getLogger(BedrockLlmClient.class);

    private final String modelId;
    private final int maxTokens;
    private final Float temperature;

    private final BedrockRuntimeClient client;

    private BedrockLlmClient(LlmConfig config, BedrockRuntimeClient client) {

        this.modelId = config.model();
        this.maxTokens = (int) Math.min(config.maxTokens(), Integer.MAX_VALUE);
        this.temperature = config.temperature() != null ? config.temperature().floatValue() : null;
        this.client = client;
    }

    public static LlmClient create(LlmConfig config) {

        if (config == null)
            throw new IllegalStateException("LLM configuration is required");

        if (!"bedrock".equals(config.provider()))
            throw new IllegalStateException("Unsupported LLM provider: " + config.provider());

        var builder = BedrockRuntimeClient.builder();

        if (config.region() != null && !config.region().isBlank())
            builder.region(Region.of(config.region()));

        if (config.apiKey() != null && !config.apiKey().isBlank()) {
            builder.credentialsProvider(StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(config.apiKey(), config.secretKey())));
        } else {
            builder.credentialsProvider(DefaultCredentialsProvider.create());
        }

        var llmClient = new BedrockLlmClient(config, builder.build());

        return LlmClient.withLogging(llmClient::send);
    }

    private LlmResponse send(LlmRequest request) {

        var systemBlock = SystemContentBlock.builder()
                .text(request.systemPrompt())
                .build();

        var userMessage = Message.builder()
                .role(ConversationRole.USER)
                .content(ContentBlock.fromText(buildUserText(request)))
                .build();

        var inferenceBuilder = InferenceConfiguration.builder().maxTokens(maxTokens);
        if (temperature != null) inferenceBuilder.temperature(temperature);

        var converseBuilder = ConverseRequest.builder()
                .modelId(modelId)
                .system(systemBlock)
                .messages(userMessage)
                .inferenceConfig(inferenceBuilder.build());

        if (request.tools() != null && !request.tools().isEmpty()) {

            var tools = new ArrayList<Tool>();

            request.tools().forEach(tool -> {

                var schema = new LinkedHashMap<String, Object>();
                schema.put("type", "object");
                schema.put("properties", tool.properties() != null ? tool.properties() : Map.of());
                schema.put("required", tool.required() != null ? tool.required() : List.of());

                tools.add(Tool.builder()
                        .toolSpec(ToolSpecification.builder()
                                .name(tool.name())
                                .description(tool.description())
                                .inputSchema(ToolInputSchema.builder()
                                        .json(toDocument(schema))
                                        .build())
                                .build())
                        .build());
            });

            converseBuilder.toolConfig(ToolConfiguration.builder().tools(tools).build());
        }

        var response = client.converse(converseBuilder.build());

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

    private static LlmResponse translate(ConverseResponse response) {

        var textBuilder = new StringBuilder();
        var toolCalls = new ArrayList<ToolCall>();

        if (response.output() != null && response.output().message() != null) {

            for (var block : response.output().message().content()) {

                if (block.text() != null) {

                    textBuilder.append(block.text());

                } else if (block.toolUse() != null) {

                    var use = block.toolUse();
                    toolCalls.add(ToolCall.of(use.toolUseId(), use.name(), asMap(use.input())));
                }
            }
        }

        var stopReason = resolveStopReason(response.stopReason(), !toolCalls.isEmpty());

        var usage = response.usage();

        long inputTotal = usage != null && usage.inputTokens() != null ? usage.inputTokens() : 0L;
        long cacheRead = usage != null && usage.cacheReadInputTokens() != null ? usage.cacheReadInputTokens() : 0L;
        long cacheWrite = usage != null && usage.cacheWriteInputTokens() != null ? usage.cacheWriteInputTokens() : 0L;
        long outputTokens = usage != null && usage.outputTokens() != null ? usage.outputTokens() : 0L;
        long inputTokens = Math.max(0, inputTotal - cacheRead);

        return LlmResponse.of(
                textBuilder.toString(),
                toolCalls,
                stopReason,
                inputTokens,
                outputTokens,
                cacheRead,
                cacheWrite,
                0L);
    }

    private static ai.agentican.framework.llm.StopReason resolveStopReason(StopReason reason, boolean hasToolCalls) {

        if (hasToolCalls) return ai.agentican.framework.llm.StopReason.TOOL_USE;

        if (reason == null) return ai.agentican.framework.llm.StopReason.END_TURN;

        return switch (reason) {
            case END_TURN, STOP_SEQUENCE -> ai.agentican.framework.llm.StopReason.END_TURN;
            case TOOL_USE -> ai.agentican.framework.llm.StopReason.TOOL_USE;
            case MAX_TOKENS -> ai.agentican.framework.llm.StopReason.MAX_TOKENS;
            default -> {
                LOG.warn("Bedrock response stopped with reason {}; mapping to END_TURN", reason);
                yield ai.agentican.framework.llm.StopReason.END_TURN;
            }
        };
    }

    private static Document toDocument(Object value) {

        if (value == null) return Document.fromNull();

        if (value instanceof Map<?, ?> m) {

            var builder = Document.mapBuilder();

            m.forEach((k, v) -> builder.putDocument(String.valueOf(k), toDocument(v)));

            return builder.build();
        }

        if (value instanceof List<?> l) {

            var items = new ArrayList<Document>();

            for (var item : l) items.add(toDocument(item));

            return Document.fromList(items);
        }

        if (value instanceof String s) return Document.fromString(s);
        if (value instanceof Boolean b) return Document.fromBoolean(b);
        if (value instanceof Integer i) return Document.fromNumber(i);
        if (value instanceof Long lng) return Document.fromNumber(lng);
        if (value instanceof Double d) return Document.fromNumber(d);
        if (value instanceof Float f) return Document.fromNumber(f);
        if (value instanceof Number n) return Document.fromNumber(String.valueOf(n));

        return Document.fromString(String.valueOf(value));
    }

    private static Map<String, Object> asMap(Document document) {

        if (document == null || document.isNull()) return Map.of();

        var obj = fromDocument(document);

        if (obj instanceof Map<?, ?> m) {
            var out = new LinkedHashMap<String, Object>();
            m.forEach((k, v) -> out.put(String.valueOf(k), v));
            return out;
        }

        return Map.of();
    }

    private static Object fromDocument(Document document) {

        if (document == null || document.isNull()) return null;
        if (document.isMap()) {
            var out = new LinkedHashMap<String, Object>();
            document.asMap().forEach((k, v) -> out.put(k, fromDocument(v)));
            return out;
        }
        if (document.isList()) {
            var out = new ArrayList<>();
            document.asList().forEach(item -> out.add(fromDocument(item)));
            return out;
        }
        if (document.isString()) return document.asString();
        if (document.isBoolean()) return document.asBoolean();
        if (document.isNumber()) return document.asNumber().bigDecimalValue();

        return null;
    }

    /** Marker to silence unused-import inspections; {@link ToolUseBlock} is reachable through {@link ContentBlock#toolUse()}. */
    @SuppressWarnings("unused") private static final Class<?> TOOL_USE_BLOCK = ToolUseBlock.class;
}
