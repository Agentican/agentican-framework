package ai.agentican.framework.agent;

import ai.agentican.framework.config.WorkerConfig;
import ai.agentican.framework.event.AgenticanEventBus;
import ai.agentican.framework.config.AgentConfig;
import ai.agentican.framework.config.LlmConfig;
import ai.agentican.framework.hitl.HitlManager;
import ai.agentican.framework.store.KnowledgeStore;
import ai.agentican.framework.llm.LlmClient;
import ai.agentican.framework.registry.SkillRegistry;
import ai.agentican.framework.store.WorkflowRunStore;
import ai.agentican.framework.util.Logs;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

public class AgentFactory {

    private static final Logger LOG = LoggerFactory.getLogger(AgentFactory.class);

    private final WorkerConfig workerConfig;
    private final List<LlmConfig> llmConfigs;
    private final HitlManager hitlManager;
    private final SkillRegistry skillRegistry;
    private final KnowledgeStore knowledgeStore;
    private final WorkflowRunStore workflowRunStore;
    private final AgenticanEventBus eventBus;

    private final Map<String, LlmClient> llms;

    private AgentFactory(WorkerConfig workerConfig, List<LlmConfig> llmConfigs, Map<String, LlmClient> llms,
                         HitlManager hitlManager, SkillRegistry skillRegistry, KnowledgeStore knowledgeStore,
                         WorkflowRunStore workflowRunStore, AgenticanEventBus eventBus) {

        this.workerConfig = workerConfig;
        this.llmConfigs = llmConfigs;
        this.llms = llms;
        this.hitlManager = hitlManager;
        this.skillRegistry = skillRegistry;
        this.knowledgeStore = knowledgeStore;
        this.workflowRunStore = workflowRunStore;
        this.eventBus = eventBus;
    }

    public Agent build(AgentConfig agentConfig) {

        var configAgentName = agentConfig.name();
        var configLlmName = agentConfig.llm();

        var defaultLlm = llms.get(LlmConfig.DEFAULT);
        var agentLlm = llms.getOrDefault(configLlmName, defaultLlm);

        if (agentLlm == null)
            throw new IllegalStateException("No LLM client found for '" + agentConfig.llm() + "' (agent: " + configAgentName + ")");

        var maxTurns = agentConfig.maxTurns() != null
                ? agentConfig.maxTurns()
                : workerConfig.maxTurns();

        var timeout = agentConfig.timeout() != null
                ? agentConfig.timeout()
                : workerConfig.timeout();

        var llmConfig = llmConfigs.stream()
                .filter(llm -> llm.name().equals(configLlmName))
                .findFirst()
                .orElse(llmConfigs.isEmpty() ? null : llmConfigs.getFirst());

        var llm = configLlmName != null ? configLlmName : LlmConfig.DEFAULT;
        var provider = llmConfig != null ? llmConfig.provider() : null;
        var model = llmConfig != null ? llmConfig.model() : null;

        var agentRunner = switch (agentConfig.runner()) {

            case AgentConfig.RUNNER_REACT -> ReActAgentRunner.builder()
                    .llmClient(agentLlm)
                    .llmName(llm)
                    .llmProvider(provider)
                    .llmModel(model)
                    .maxIterations(maxTurns)
                    .timeout(timeout)
                    .workflowRunStore(workflowRunStore)
                    .eventBus(eventBus)
                    .build();

            default -> SmacAgentRunner.builder()
                    .llmClient(agentLlm)
                    .llmName(llm)
                    .llmProvider(provider)
                    .llmModel(model)
                    .maxIterations(maxTurns)
                    .timeout(timeout)
                    .hitlManager(hitlManager)
                    .knowledgeStore(knowledgeStore)
                    .workflowRunStore(workflowRunStore)
                    .skillRegistry(skillRegistry)
                    .eventBus(eventBus)
                    .build();
        };

        LOG.info(Logs.AGENTICAN_BUILT_AGENT, configAgentName);

        return Agent.builder().config(agentConfig).runner(agentRunner).build();
    }

    public static Builder builder() {

        return new Builder();
    }

    public static class Builder {

        private WorkerConfig workerConfig;
        private List<LlmConfig> llmConfigs = List.of();
        private HitlManager hitlManager;
        private SkillRegistry skillRegistry;
        private KnowledgeStore knowledgeStore;
        private WorkflowRunStore workflowRunStore;
        private AgenticanEventBus eventBus;

        private Map<String, LlmClient> llms;

        Builder() {}

        public Builder workerConfig(WorkerConfig workerConfig) { this.workerConfig = workerConfig; return this; }
        public Builder llmConfigs(List<LlmConfig> llmConfigs) { this.llmConfigs = llmConfigs != null ? llmConfigs : List.of(); return this; }
        public Builder hitlManager(HitlManager hitlManager) { this.hitlManager = hitlManager; return this; }
        public Builder skillRegistry(SkillRegistry skillRegistry) { this.skillRegistry = skillRegistry; return this; }
        public Builder knowledgeStore(KnowledgeStore knowledgeStore) { this.knowledgeStore = knowledgeStore; return this; }
        public Builder workflowRunStore(WorkflowRunStore workflowRunStore) { this.workflowRunStore = workflowRunStore; return this; }
        public Builder eventBus(AgenticanEventBus eventBus) { this.eventBus = eventBus; return this; }

        public Builder llms(Map<String, LlmClient> llms) { this.llms = llms; return this; }

        public AgentFactory build() {

            return new AgentFactory(workerConfig, llmConfigs, llms, hitlManager, skillRegistry, knowledgeStore,
                    workflowRunStore, eventBus);
        }
    }
}
