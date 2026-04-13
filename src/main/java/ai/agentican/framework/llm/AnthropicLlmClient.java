package ai.agentican.framework.llm;

import ai.agentican.framework.config.LlmConfig;
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

    private final String model;
    private final long maxTokens;

    private final AnthropicClient client;

    private AnthropicLlmClient(LlmConfig config) {

        this.client = AnthropicOkHttpClient.builder().apiKey(config.apiKey()).build();
        this.model = config.model();
        this.maxTokens = config.maxTokens();
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

        var systemPromptBlock = TextBlockParam.builder()
                .text(request.systemPrompt())
                .cacheControl(CACHE_CONTROL)
                .build();

        var userMessageBlock = TextBlockParam.builder()
                .text(request.userMessage())
                .cacheControl(CACHE_CONTROL)
                .build();

        var userBlocks = new ArrayList<ContentBlockParam>();

        userBlocks.add(ContentBlockParam.ofText(userMessageBlock));

        var userMessageParam = MessageParam.builder()
                .role(MessageParam.Role.USER)
                .contentOfBlockParams(userBlocks)
                .build();

        var messageBuilder = MessageCreateParams.builder()
                .model(Model.of(model))
                .maxTokens(maxTokens)
                .systemOfTextBlockParams(List.of(systemPromptBlock))
                .messages(List.of(userMessageParam));

        if (request.tools() != null) {

            request.tools().stream().map(tool -> {

                var schemaBuilder = Tool.InputSchema.builder().type(JsonValue.from("object"));

                if (tool.properties() != null && !tool.properties().isEmpty())
                    schemaBuilder.properties(JsonValue.from(tool.properties()));

                if (tool.required() != null && !tool.required().isEmpty())
                    schemaBuilder.required(JsonValue.from(tool.required()));

                return Tool.builder()
                        .name(tool.name())
                        .description(tool.description())
                        .inputSchema(schemaBuilder.build())
                        .build();

            }).forEach(messageBuilder::addTool);
        }

        messageBuilder.addTool(WebSearchTool20250305.builder().build());
        messageBuilder.addTool(WebFetchTool20250910.builder().build());

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

                    return ToolCall.of(toolUse.id(), toolUse.name(), toolArgs != null ? toolArgs : Map.of());

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

        return LlmResponse.of(responseText, toolCalls, stopReason, inputTokens, outputTokens,
                cacheReadTokens, cacheWriteTokens, webSearchRequests);
    }

    private <T> T sendStructured(LlmRequest request, Class<T> outputType) {

        var systemPromptBlock = com.anthropic.models.beta.messages.BetaTextBlockParam.builder()
                .text(request.systemPrompt())
                .cacheControl(com.anthropic.models.beta.messages.BetaCacheControlEphemeral.builder().build())
                .build();

        var params = com.anthropic.models.beta.messages.MessageCreateParams.builder()
                .model(Model.of(model))
                .maxTokens(maxTokens)
                .outputConfig(outputType)
                .systemOfBetaTextBlockParams(List.of(systemPromptBlock))
                .addUserMessage(request.userMessage())
                .build();

        var response = client.beta().messages().create(params);

        return response.content().getFirst().text().orElseThrow().text();
    }
}
