package ai.agentican.framework.llm.provider;

import ai.agentican.framework.config.LlmConfig;
import ai.agentican.framework.llm.*;
import ai.agentican.framework.llm.StopReason;
import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.JsonValue;
import com.anthropic.models.messages.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class AnthropicLlmClient {

    private static final Logger LOG = LoggerFactory.getLogger(AnthropicLlmClient.class);
    private static final CacheControlEphemeral CACHE_CONTROL = CacheControlEphemeral.builder().build();
    private static final ObjectMapper JSON = new ObjectMapper();

    private final String model;
    private final long maxTokens;
    private final Double temperature;

    private final AnthropicClient client;

    private AnthropicLlmClient(LlmConfig config) {

        this.client = AnthropicOkHttpClient.builder().apiKey(config.apiKey()).build();
        this.model = config.model();
        this.maxTokens = config.maxTokens();
        this.temperature = config.temperature();
    }

    private static AnthropicLlmClient of(LlmConfig llmConfig) {

        return new AnthropicLlmClient(llmConfig);
    }

    public static LlmClient create(LlmConfig config) {

        if (config == null)
            throw new IllegalStateException("LLM configuration is required");

        if (!"anthropic".equals(config.provider()))
            throw new IllegalStateException("Unsupported LLM provider: " + config.provider());

        var llmClient = AnthropicLlmClient.of(config);

        return LlmClient.withLogging(llmClient::send);
    }

    private LlmResponse send(LlmRequest request) {

        var hasNativeSchema = request.structuredOutput() != null;

        var systemPromptBlock = TextBlockParam.builder()
                .text(request.systemPrompt())
                .cacheControl(CACHE_CONTROL)
                .build();

        var messages = request.messages().isEmpty()
                ? legacySingleUserMessage(request)
                : translateMessages(request.messages());

        var messageBuilder = MessageCreateParams.builder()
                .model(Model.of(model))
                .maxTokens(maxTokens)
                .systemOfTextBlockParams(List.of(systemPromptBlock))
                .messages(messages);

        if (temperature != null) messageBuilder.temperature(temperature);

        if (hasNativeSchema)
            messageBuilder.outputConfig(buildOutputConfig(request.structuredOutput()));

        var toolBuilders = new ArrayList<Tool.Builder>();

        if (request.tools() != null) {

            request.tools().forEach(tool -> {

                var schemaBuilder = Tool.InputSchema.builder().type(JsonValue.from("object"));

                if (tool.properties() != null && !tool.properties().isEmpty())
                    schemaBuilder.properties(JsonValue.from(tool.properties()));

                if (tool.required() != null && !tool.required().isEmpty())
                    schemaBuilder.required(JsonValue.from(tool.required()));

                toolBuilders.add(Tool.builder()
                        .name(tool.name())
                        .description(tool.description())
                        .inputSchema(schemaBuilder.build()));
            });
        }

        for (var tb : toolBuilders) messageBuilder.addTool(tb.build());

        messageBuilder.addTool(WebSearchTool20250305.builder().build());

        messageBuilder.addTool(WebFetchTool20250910.builder().cacheControl(CACHE_CONTROL).build());

        var response = client.messages().create(messageBuilder.build());

        var responseText = response.content().stream()
                .flatMap(block -> block.text().stream())
                .map(TextBlock::text)
                .collect(Collectors.joining());

        var toolCalls = response.content().stream()
                .filter(ContentBlock::isToolUse)
                .flatMap(block -> block.toolUse().stream())
                .map(toolUse -> {

                    Map<String, Object> toolArgs = toolUse._input().convert(new TypeReference<>() {});

                    return new ToolCall(toolUse.id(), toolUse.name(), toolArgs != null ? toolArgs : Map.of());

                }).toList();

        var stopReason = switch (response.stopReason().orElseThrow().asString()) {

            case "tool_use" -> StopReason.TOOL_USE;
            case "max_tokens" -> StopReason.MAX_TOKENS;
            default -> StopReason.END_TURN;
        };

        var usage = response.usage();

        long inputTokens = usage.inputTokens();
        long outputTokens = usage.outputTokens();
        long cacheWriteTokens = usage.cacheCreationInputTokens().orElse(0L);
        long cacheReadTokens = usage.cacheReadInputTokens().orElse(0L);
        long webSearchRequests =
                usage.serverToolUse().map(stu -> (long) stu.webSearchRequests()).orElse(0L);

        return new LlmResponse(responseText, toolCalls, stopReason, inputTokens, outputTokens,
                cacheReadTokens, cacheWriteTokens, webSearchRequests);
    }

    private static List<MessageParam> legacySingleUserMessage(LlmRequest request) {

        var userBlocks = new ArrayList<ContentBlockParam>();

        if (request.userTask() != null && !request.userTask().isBlank()) {

            var userTaskBlock = TextBlockParam.builder()
                    .text(request.userTask())
                    .cacheControl(CACHE_CONTROL)
                    .build();

            userBlocks.add(ContentBlockParam.ofText(userTaskBlock));
        }

        if (request.userMessage() != null && !request.userMessage().isBlank()) {

            var userMessageBlock = TextBlockParam.builder()
                    .text(request.userMessage())
                    .build();

            userBlocks.add(ContentBlockParam.ofText(userMessageBlock));
        }

        return List.of(MessageParam.builder()
                .role(MessageParam.Role.USER)
                .contentOfBlockParams(userBlocks)
                .build());
    }

    private static List<MessageParam> translateMessages(List<ai.agentican.framework.llm.Message> messages) {

        var out = new ArrayList<MessageParam>(messages.size());

        var firstUserSeen = false;

        for (var msg : messages) {

            var blocks = new ArrayList<ContentBlockParam>(msg.blocks().size());

            var isFirstUser = !firstUserSeen && msg.role() == ai.agentican.framework.llm.Message.Role.USER;

            var lastIdx = msg.blocks().size() - 1;

            for (int i = 0; i < msg.blocks().size(); i++) {

                var block = msg.blocks().get(i);
                var applyCache = isFirstUser && i == lastIdx;

                blocks.add(translateBlock(block, applyCache));
            }

            out.add(MessageParam.builder()
                    .role(msg.role() == ai.agentican.framework.llm.Message.Role.USER
                            ? MessageParam.Role.USER : MessageParam.Role.ASSISTANT)
                    .contentOfBlockParams(blocks)
                    .build());

            if (isFirstUser) firstUserSeen = true;
        }

        return out;
    }

    private static ContentBlockParam translateBlock(ai.agentican.framework.llm.Message.Block block, boolean applyCache) {

        return switch (block) {

            case ai.agentican.framework.llm.Message.TextBlock t -> {

                var b = TextBlockParam.builder().text(t.text());
                if (applyCache) b.cacheControl(CACHE_CONTROL);
                yield ContentBlockParam.ofText(b.build());
            }

            case ai.agentican.framework.llm.Message.ToolUseBlock tu -> {

                var inputJson = JSON.<JsonValue>convertValue(tu.args(), new TypeReference<JsonValue>() {});
                yield ContentBlockParam.ofToolUse(ToolUseBlockParam.builder()
                        .id(tu.id())
                        .name(tu.toolName())
                        .input(inputJson != null ? inputJson : JsonValue.from(Map.of()))
                        .build());
            }

            case ai.agentican.framework.llm.Message.ToolResultBlock tr -> {

                var b = ToolResultBlockParam.builder()
                        .toolUseId(tr.toolUseId())
                        .content(tr.content());
                if (tr.isError()) b.isError(true);
                yield ContentBlockParam.ofToolResult(b.build());
            }
        };
    }

    private static OutputConfig buildOutputConfig(StructuredOutput so) {

        var schemaBuilder = JsonOutputFormat.Schema.builder();

        var fields = JSON.<Map<String, Object>>convertValue(so.schema(),
                new TypeReference<Map<String, Object>>() {});
        fields.forEach((k, v) -> schemaBuilder.putAdditionalProperty(k, JsonValue.from(v)));

        var format = JsonOutputFormat.builder().schema(schemaBuilder.build()).build();

        return OutputConfig.builder().format(format).build();
    }
}
