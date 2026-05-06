package ai.agentican.quarkus;

import ai.agentican.framework.config.ComposioConfig;
import ai.agentican.framework.config.LlmConfig;
import ai.agentican.framework.config.McpConfig;
import ai.agentican.framework.config.RuntimeConfig;
import ai.agentican.framework.config.WorkerConfig;

import java.util.ArrayList;
import java.util.List;

final class RuntimeConfigConverter {

    private RuntimeConfigConverter() {}

    static RuntimeConfig fromProperties(AgenticanConfig source) {

        var llms = new ArrayList<LlmConfig>();
        var mcps = new ArrayList<McpConfig>();

        source.llm().forEach(llm -> llms.add(toLlmConfig(llm)));
        source.mcp().forEach(mcp -> mcps.add(toMcpConfig(mcp)));

        var worker = source.agentRunner().map(RuntimeConfigConverter::toWorkerConfig).orElse(null);
        var composio = source.composio().map(RuntimeConfigConverter::toComposioConfig).orElse(null);

        return new RuntimeConfig(llms, mcps, composio, worker, List.of(), List.of(), List.of(), source.strict());
    }

    private static LlmConfig toLlmConfig(AgenticanConfig.LlmConfig source) {

        var builder = LlmConfig.builder()
                .name(source.name())
                .provider(source.provider())
                .apiKey(source.apiKey())
                .maxTokens(source.maxTokens());

        source.model().ifPresent(builder::model);

        return builder.build();
    }

    private static WorkerConfig toWorkerConfig(AgenticanConfig.AgentRunnerConfig source) {

        var builder = WorkerConfig.builder()
                .maxTurns(source.maxTurns())
                .timeout(source.timeout());

        source.taskTimeout().ifPresent(builder::taskTimeout);

        return builder.build();
    }

    private static ComposioConfig toComposioConfig(AgenticanConfig.ComposioConfig source) {

        return ComposioConfig.builder()
                .apiKey(source.apiKey())
                .userId(source.userId())
                .build();
    }

    private static McpConfig toMcpConfig(AgenticanConfig.McpConfig source) {

        var builder = McpConfig.builder()
                .slug(source.slug())
                .name(source.name())
                .url(source.url());

        source.queryParams().forEach(builder::queryParam);
        source.headers().forEach(builder::header);

        return builder.build();
    }
}
