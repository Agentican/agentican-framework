package ai.agentican.framework.agent;

import ai.agentican.framework.TaskListener;
import ai.agentican.framework.config.AgentConfig;
import ai.agentican.framework.config.LlmConfig;
import ai.agentican.framework.config.RuntimeConfig;
import ai.agentican.framework.hitl.HitlManager;
import ai.agentican.framework.knowledge.KnowledgeStore;
import ai.agentican.framework.llm.LlmClient;
import ai.agentican.framework.skill.SkillRegistry;
import ai.agentican.framework.state.TaskStateStore;
import ai.agentican.framework.util.Logs;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.function.Function;

public class AgentFactory implements Function<AgentConfig, Agent> {

    private static final Logger LOG = LoggerFactory.getLogger(AgentFactory.class);

    private final RuntimeConfig config;
    private final Map<String, LlmClient> llms;
    private final HitlManager hitlManager;
    private final KnowledgeStore knowledgeStore;
    private final TaskStateStore taskStateStore;
    private final SkillRegistry skillRegistry;
    private final TaskListener taskListener;

    public AgentFactory(RuntimeConfig config, Map<String, LlmClient> llms, HitlManager hitlManager,
                        KnowledgeStore knowledgeStore, TaskStateStore taskStateStore,
                        SkillRegistry skillRegistry, TaskListener taskListener) {

        this.config = config;
        this.llms = llms;
        this.hitlManager = hitlManager;
        this.knowledgeStore = knowledgeStore;
        this.taskStateStore = taskStateStore;
        this.skillRegistry = skillRegistry;
        this.taskListener = taskListener;
    }

    @Override
    public Agent apply(AgentConfig agentConfig) {

        return build(agentConfig);
    }

    public Agent build(AgentConfig agentConfig) {

        var agentName = agentConfig.name();
        var llmName = agentConfig.llm();
        var defaultLlm = llms.get(LlmConfig.DEFAULT);

        var agentLlm = llms.getOrDefault(llmName, defaultLlm);

        if (agentLlm == null)
            throw new IllegalStateException("No LLM client found for '" + agentConfig.llm() + "' (agent: " + agentName + ")");

        var agentRunnerConfig = config.agentRunner();

        var maxTurns = agentRunnerConfig.maxTurns();
        var timeout = agentRunnerConfig.timeout();

        var llmConfig = config.llm().stream()
                .filter(llm -> llm.name().equals(llmName))
                .findFirst()
                .orElse(config.llm().isEmpty() ? null : config.llm().getFirst());

        var agentRunner = SmacAgentRunner.builder()
                .llmClient(agentLlm)
                .llmName(llmName != null ? llmName : LlmConfig.DEFAULT)
                .llmProvider(llmConfig != null ? llmConfig.provider() : null)
                .llmModel(llmConfig != null ? llmConfig.model() : null)
                .maxIterations(maxTurns)
                .timeout(timeout)
                .hitlManager(hitlManager)
                .knowledgeStore(knowledgeStore)
                .taskStateStore(taskStateStore)
                .skillRegistry(skillRegistry)
                .taskListener(taskListener)
                .build();

        LOG.info(Logs.AGENTICAN_BUILT_AGENT, agentName);

        return Agent.of(agentConfig, agentRunner);
    }
}
