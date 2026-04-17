package ai.agentican.quarkus;

import ai.agentican.framework.config.AgentConfig;
import ai.agentican.framework.config.ComposioConfig;
import ai.agentican.framework.config.LlmConfig;
import ai.agentican.framework.config.McpConfig;
import ai.agentican.framework.config.RuntimeConfig;
import ai.agentican.framework.config.SkillConfig;
import ai.agentican.framework.config.WorkerConfig;

final class RuntimeConfigConverter {

    private RuntimeConfigConverter() {}

    static RuntimeConfig toRuntimeConfig(AgenticanConfig source) {

        var builder = RuntimeConfig.builder();

        source.llm().forEach(llm -> builder.llm(toLlmConfig(llm)));

        source.agentRunner().ifPresent(runner -> builder.worker(toWorkerConfig(runner)));

        source.composio().ifPresent(composio -> builder.composio(toComposioConfig(composio)));

        source.mcp().forEach(mcp -> builder.mcp(toMcpConfig(mcp)));

        source.agents().forEach(agent -> builder.agent(toAgentConfig(agent)));

        source.skills().forEach(skill -> builder.skill(toSkillConfig(skill)));

        return builder.build();
    }

    private static SkillConfig toSkillConfig(AgenticanConfig.SkillConfig source) {

        return new SkillConfig(source.id().orElse(null), source.name(), source.instructions(),
                source.externalId().orElse(null));
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

    private static AgentConfig toAgentConfig(AgenticanConfig.AgentConfig source) {

        var builder = AgentConfig.builder()
                .name(source.name())
                .role(source.role());

        source.id().ifPresent(builder::id);
        source.externalId().ifPresent(builder::externalId);
        source.llm().ifPresent(builder::llm);

        return builder.build();
    }
}
