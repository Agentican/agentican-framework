package ai.agentican.quarkus;

import ai.agentican.framework.config.ComposioConfig;
import ai.agentican.framework.config.EngineConfig;
import ai.agentican.framework.config.LlmConfig;
import ai.agentican.framework.config.McpConfig;
import ai.agentican.framework.config.WorkerConfig;

import java.util.ArrayList;

final class EngineConfigConverter {

    private EngineConfigConverter() {}

    /** Build an {@link EngineConfig} from the {@code agentican.*} property surface. */
    static EngineConfig fromProperties(AgenticanConfig source) {

        var llms = new ArrayList<LlmConfig>();
        var mcps = new ArrayList<McpConfig>();

        source.llm().forEach(llm -> llms.add(toLlmConfig(llm)));
        source.mcp().forEach(mcp -> mcps.add(toMcpConfig(mcp)));

        var worker = source.agentRunner().map(EngineConfigConverter::toWorkerConfig).orElse(null);
        var composio = source.composio().map(EngineConfigConverter::toComposioConfig).orElse(null);

        return new EngineConfig(llms, mcps, composio, worker, source.strict());
    }

    /**
     * Merge a YAML-loaded {@link EngineConfig} with one built from properties. YAML wins
     * for any field set in both: lists come from YAML when non-empty, scalar fields from
     * YAML when present, {@code strict} is OR'd. (Properties surface defaults from
     * {@code @WithDefault} annotations, so a real YAML override should beat them.)
     */
    static EngineConfig merge(EngineConfig fromYaml, EngineConfig fromProps) {

        return new EngineConfig(
                !fromYaml.llm().isEmpty()      ? fromYaml.llm()         : fromProps.llm(),
                !fromYaml.mcp().isEmpty()      ? fromYaml.mcp()         : fromProps.mcp(),
                fromYaml.composio() != null    ? fromYaml.composio()    : fromProps.composio(),
                fromYaml.agentRunner() != null ? fromYaml.agentRunner() : fromProps.agentRunner(),
                fromYaml.strict() || fromProps.strict());
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
