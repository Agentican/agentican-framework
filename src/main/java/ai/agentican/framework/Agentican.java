package ai.agentican.framework;

import ai.agentican.framework.agent.AgentFactory;
import ai.agentican.framework.agent.AgentRegistry;
import ai.agentican.framework.agent.InMemoryAgentRegistry;
import ai.agentican.framework.config.AgentConfig;
import ai.agentican.framework.config.ComposioConfig;
import ai.agentican.framework.config.LlmConfig;
import ai.agentican.framework.config.McpConfig;
import ai.agentican.framework.config.PlanConfig;
import ai.agentican.framework.config.RuntimeConfig;
import ai.agentican.framework.config.SkillConfig;
import ai.agentican.framework.hitl.HitlManager;
import ai.agentican.framework.hitl.HitlNotifier;
import ai.agentican.framework.knowledge.KnowledgeIngestor;
import ai.agentican.framework.knowledge.KnowledgeStore;
import ai.agentican.framework.knowledge.LlmKnowledgeExtractor;
import ai.agentican.framework.knowledge.MemKnowledgeStore;
import ai.agentican.framework.llm.AnthropicLlmClient;
import ai.agentican.framework.llm.LlmClient;
import ai.agentican.framework.llm.LlmClientDecorator;
import ai.agentican.framework.llm.RetryingLlmClient;
import ai.agentican.framework.state.MemTaskStateStore;
import ai.agentican.framework.state.NotifyingTaskStateStore;
import ai.agentican.framework.state.TaskStateStore;
import ai.agentican.framework.orchestration.InMemoryPlanRegistry;
import ai.agentican.framework.orchestration.PlanRegistry;
import ai.agentican.framework.skill.InMemorySkillRegistry;
import ai.agentican.framework.skill.SkillRegistry;
import ai.agentican.framework.orchestration.execution.TaskHandle;
import ai.agentican.framework.orchestration.execution.TaskRunner;
import ai.agentican.framework.orchestration.execution.TaskStatus;
import ai.agentican.framework.util.Ids;
import ai.agentican.framework.util.Mdc;
import ai.agentican.framework.orchestration.model.Plan;
import ai.agentican.framework.orchestration.planning.PlannerAgent;
import ai.agentican.framework.tools.Toolkit;
import ai.agentican.framework.tools.ToolkitRegistry;
import ai.agentican.framework.tools.composio.ComposioClient;
import ai.agentican.framework.tools.mcp.McpToolkit;
import ai.agentican.framework.util.Logs;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import java.util.stream.Stream;

public class Agentican implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(Agentican.class);

    private final RuntimeConfig config;

    private final Map<String, LlmClient> llms;

    private final PlanRegistry planRegistry;
    private final AgentRegistry agentRegistry;
    private final ToolkitRegistry toolkitRegistry;
    private final SkillRegistry skillRegistry;
    private final AgentFactory agentFactory;

    private final PlannerAgent taskPlanner;
    private final TaskRunner taskRunner;
    private final TaskDecorator taskDecorator;
    private final TaskListener taskListener;
    private final TaskStateStore taskStateStore;
    private final HitlManager hitlManager;
    private final KnowledgeStore knowledgeStore;

    private final ExecutorService taskExecutor;
    private final boolean ownsExecutor;

    public static AgenticanBuilder builder() {

        return new AgenticanBuilder();
    }

    @SuppressWarnings("unused")
    private Agentican(RuntimeConfig config, Map<String, LlmClient> llms, Map<String, Toolkit> toolkits,
                      List<AgentConfig> extraAgents, List<SkillConfig> extraSkills, List<PlanConfig> extraPlans,
                      List<McpConfig> extraMcp, ComposioConfig extraComposio,
                      TaskStateStore taskStateStore, HitlManager hitlManager, KnowledgeStore knowledgeStore,
                      AgentRegistry agentRegistryOverride, SkillRegistry skillRegistryOverride,
                      PlanRegistry planRegistryOverride,
                      LlmClientDecorator llmDecorator, TaskDecorator taskDecorator, TaskListener taskListener,
                      ExecutorService taskExecutor) {

        this.config = config;

        this.taskDecorator = taskDecorator;
        this.taskListener = taskListener != null ? taskListener : new TaskListener() {};
        this.hitlManager = hitlManager;
        this.knowledgeStore = knowledgeStore;

        this.ownsExecutor = (taskExecutor == null);
        this.taskExecutor = taskExecutor != null ? taskExecutor : Executors.newVirtualThreadPerTaskExecutor();

        var mutableLlms = new LinkedHashMap<String, LlmClient>();

        var agentRunnerConfig = config.agentRunner();

        config.llm().forEach(llmConfig -> {

            var client = AnthropicLlmClient.create(llmConfig);

            if (llmDecorator != null) client = llmDecorator.decorate(llmConfig, client);

            client = new RetryingLlmClient(client,
                    agentRunnerConfig.llmMaxRetries(), agentRunnerConfig.llmRetryBaseDelay());

            mutableLlms.put(llmConfig.name(), client);
        });

        mutableLlms.putAll(llms);

        this.llms = Collections.unmodifiableMap(mutableLlms);

        TaskStateStore notifyingStore = new NotifyingTaskStateStore(taskStateStore, this.taskListener);

        if (knowledgeStore != null) {

            var defaultLlmForKnowledge = this.llms.get(LlmConfig.DEFAULT);

            if (defaultLlmForKnowledge != null) {

                var extractor = new LlmKnowledgeExtractor(defaultLlmForKnowledge);
                var ingestor = new KnowledgeIngestor(taskStateStore, knowledgeStore, extractor, this.taskExecutor);

                notifyingStore = new NotifyingTaskStateStore(notifyingStore, ingestor);
            }
        }

        this.taskStateStore = notifyingStore;

        var toolkitRegistry = new ToolkitRegistry();

        Stream.concat(
                config.mcp() != null ? config.mcp().stream() : Stream.<McpConfig>empty(),
                extraMcp != null ? extraMcp.stream() : Stream.<McpConfig>empty()
        ).forEach(mcpConfig -> toolkitRegistry.register(mcpConfig.slug(), McpToolkit.of(mcpConfig)));

        var composioConfig = extraComposio != null ? extraComposio : config.composio();

        if (extraComposio != null && config.composio() != null)
            LOG.warn("Both RuntimeConfig.composio() and builder.composio(...) provided — using the builder value");

        if (composioConfig != null && composioConfig.apiKey() != null) {

            var composioApiKey = composioConfig.apiKey();
            var composioUserId = composioConfig.userId();

            var composioClient = ComposioClient.of(composioApiKey, composioUserId);

            composioClient.availableToolkits().forEach(composioToolkit ->
                    toolkitRegistry.register(composioToolkit.slug(), composioToolkit));
        }

        toolkits.forEach(toolkitRegistry::register);

        this.toolkitRegistry = toolkitRegistry;

        var defaultLlm = this.llms.get(LlmConfig.DEFAULT);

        SkillRegistry skillRegistry = skillRegistryOverride != null ? skillRegistryOverride : new InMemorySkillRegistry();
        skillRegistry.seed();

        Stream.concat(
                config.skills() != null ? config.skills().stream() : Stream.<SkillConfig>empty(),
                extraSkills != null ? extraSkills.stream() : Stream.<SkillConfig>empty()
        ).forEach(skill -> {
            requireExternalId("skill", skill.name(), skill.externalId());
            skillRegistry.register(skill);
        });

        this.skillRegistry = skillRegistry;

        var agentFactory = new AgentFactory(config, this.llms, this.hitlManager,
                this.knowledgeStore, this.taskStateStore, skillRegistry, this.taskListener);
        this.agentFactory = agentFactory;

        AgentRegistry agentRegistry = agentRegistryOverride != null ? agentRegistryOverride : new InMemoryAgentRegistry();
        agentRegistry.seed(agentFactory);

        Stream.concat(
                config.agents() != null ? config.agents().stream() : Stream.<AgentConfig>empty(),
                extraAgents != null ? extraAgents.stream() : Stream.<AgentConfig>empty()
        ).forEach(agentConfig -> {
            requireExternalId("agent", agentConfig.name(), agentConfig.externalId());
            agentRegistry.register(agentFactory.build(agentConfig));
        });

        this.agentRegistry = agentRegistry;

        PlanRegistry planRegistry = planRegistryOverride != null ? planRegistryOverride : new InMemoryPlanRegistry();
        planRegistry.seed();

        Stream.concat(
                config.plans() != null ? config.plans().stream() : Stream.<PlanConfig>empty(),
                extraPlans != null ? extraPlans.stream() : Stream.<PlanConfig>empty()
        ).forEach(planConfig -> {
            requireExternalId("plan", planConfig.name(), planConfig.externalId());
            planRegistry.register(planConfig.toPlan());
        });

        this.planRegistry = planRegistry;

        this.taskPlanner = new PlannerAgent(defaultLlm, agentRegistry, toolkitRegistry, skillRegistry,
                planRegistry, agentFactory);

        var taskTimeout = agentRunnerConfig.taskTimeout();
        var maxStepRetries = agentRunnerConfig.maxStepRetries();

        this.taskRunner = new TaskRunner(agentRegistry, hitlManager, toolkitRegistry, this.taskStateStore,
                taskTimeout, maxStepRetries, taskDecorator);

        var numLlms = this.llms.size();
        var numToolkits = toolkitRegistry.slugs().size();
        var numAgents = agentRegistry.asMap().size();
        var numTasks = planRegistry.asMap().size();

        LOG.info(Logs.AGENTICAN_INIT, numLlms, numToolkits, numAgents, numTasks);
    }

    public TaskHandle run(Plan plan) {

        return run(plan, Map.of());
    }

    public TaskHandle run(Plan plan, Map<String, String> taskInputs) {

        var taskCancelled = new AtomicBoolean(false);
        var taskId = Ids.generate();

        var asyncTaskRunner = wrapTaskRunner(Mdc.propagate(() -> {

            try {

                planRegistry.registerIfAbsent(plan);

                taskListener.onPlanCompleted(taskId, plan.id());

                return taskRunner.run(plan, taskId, taskInputs, taskCancelled);

            }
            catch (Exception e) {

                LOG.error("Task {} failed: {}", taskId, e.getMessage(), e);

                taskListener.onTaskCompleted(taskId, TaskStatus.FAILED);

                throw e;
            }
        }));

        var asyncTaskResult = CompletableFuture.supplyAsync(asyncTaskRunner, taskExecutor);

        return new TaskHandle(taskId, asyncTaskResult, taskCancelled);
    }

    public TaskHandle run(String taskDescription) {

        var taskCancelled = new AtomicBoolean(false);
        var taskId = Ids.generate();

        var asyncTaskRunner = wrapTaskRunner(Mdc.propagate(() -> {

            MDC.put("taskId", "[" + taskId + "] ");

            try {

                LOG.info(Logs.AGENTICAN_DEL_TASK);
                LOG.debug(Logs.AGENTICAN_DEL_TASK_FULL, taskDescription);

                taskListener.onPlanStarted(taskId);

                var planningResult = taskPlanner.plan(taskDescription);
                var plan = planningResult.plan();

                planRegistry.registerIfAbsent(plan);

                taskListener.onPlanCompleted(taskId, plan.id());

                return taskRunner.run(plan, taskId, planningResult.inputs(), taskCancelled);

            }
            catch (Exception e) {

                LOG.error("Task {} failed: {}", taskId, e.getMessage(), e);

                taskListener.onTaskCompleted(taskId, TaskStatus.FAILED);

                throw e;
            }

        }));

        var taskResult = CompletableFuture.supplyAsync(asyncTaskRunner, taskExecutor);

        return new TaskHandle(taskId, taskResult, taskCancelled);
    }

    private <T> Supplier<T> wrapTaskRunner(Supplier<T> supplier) {

        return taskDecorator != null ? taskDecorator.decorate(supplier) : supplier;
    }

    public PlanRegistry plans() {

        return planRegistry;
    }

    public AgentRegistry agents() {

        return agentRegistry;
    }

    public ToolkitRegistry toolkits() {

        return toolkitRegistry;
    }

    public SkillRegistry skills() {

        return skillRegistry;
    }

    public HitlManager hitlManager() {

        return hitlManager;
    }

    private static void requireExternalId(String kind, String name, String externalId) {

        if (externalId == null || externalId.isBlank())
            throw new IllegalStateException(kind + " '" + name + "' is missing an externalId. "
                    + "Config-file and fluent-builder " + kind + "s must declare a stable externalId "
                    + "so the catalog can upsert consistently across deploys.");
    }

    @Override
    public void close() {

        if (ownsExecutor)
            taskExecutor.shutdownNow();

        toolkitRegistry.close();
    }

    public static class AgenticanBuilder {

        private RuntimeConfig config;

        private final Map<String, LlmClient> llms = new LinkedHashMap<>();
        private final Map<String, Toolkit> toolkits = new LinkedHashMap<>();

        private final List<AgentConfig> extraAgents = new ArrayList<>();
        private final List<SkillConfig> extraSkills = new ArrayList<>();
        private final List<PlanConfig> extraPlans = new ArrayList<>();
        private final List<McpConfig> extraMcp = new ArrayList<>();
        private ComposioConfig extraComposio;

        private HitlManager hitlManager;
        private KnowledgeStore knowledgeStore;
        private AgentRegistry agentRegistry;
        private SkillRegistry skillRegistry;
        private PlanRegistry planRegistry;
        private LlmClientDecorator llmDecorator;
        private TaskDecorator taskDecorator;
        private TaskListener stepListener;
        private TaskStateStore taskStateStore;
        private ExecutorService taskExecutor;

        public AgenticanBuilder config(RuntimeConfig config) { this.config = config; return this; }
        public AgenticanBuilder llm(String name, LlmClient llm) { this.llms.put(name, llm); return this; }
        public AgenticanBuilder llmDecorator(LlmClientDecorator llmDecorator) { this.llmDecorator = llmDecorator; return this; }

        public AgenticanBuilder agent(AgentConfig agent) { this.extraAgents.add(agent); return this; }
        public AgenticanBuilder skill(SkillConfig skill) { this.extraSkills.add(skill); return this; }
        public AgenticanBuilder plan(PlanConfig plan)   { this.extraPlans.add(plan);   return this; }
        public AgenticanBuilder mcp(McpConfig mcp)      { this.extraMcp.add(mcp);      return this; }
        public AgenticanBuilder composio(ComposioConfig composio) { this.extraComposio = composio; return this; }

        public AgenticanBuilder toolkit(String slug, Toolkit toolkit) { this.toolkits.put(slug, toolkit); return this; }

        public AgenticanBuilder hitlManager(HitlManager hitlManager) { this.hitlManager = hitlManager; return this; }
        public AgenticanBuilder knowledgeStore(KnowledgeStore knowledgeStore) { this.knowledgeStore = knowledgeStore; return this; }
        public AgenticanBuilder agentRegistry(AgentRegistry agentRegistry) { this.agentRegistry = agentRegistry; return this; }
        public AgenticanBuilder skillRegistry(SkillRegistry skillRegistry) { this.skillRegistry = skillRegistry; return this; }
        public AgenticanBuilder planRegistry(PlanRegistry planRegistry) { this.planRegistry = planRegistry; return this; }
        public AgenticanBuilder taskDecorator(TaskDecorator taskDecorator) { this.taskDecorator = taskDecorator; return this; }
        public AgenticanBuilder stepListener(TaskListener taskListener) { this.stepListener = taskListener; return this; }
        public AgenticanBuilder taskStateStore(TaskStateStore taskStateStore) { this.taskStateStore = taskStateStore; return this; }
        public AgenticanBuilder taskExecutor(ExecutorService taskExecutor) { this.taskExecutor = taskExecutor; return this; }

        public Agentican build() {

            if (config == null)
                throw new IllegalStateException("RuntimeConfig is required");

            hitlManager = hitlManager != null ? hitlManager : new HitlManager(HitlNotifier.logging());
            knowledgeStore = knowledgeStore != null ? knowledgeStore : new MemKnowledgeStore();
            taskStateStore = taskStateStore != null ? taskStateStore : new MemTaskStateStore();

            return new Agentican(config, llms, toolkits,
                    extraAgents, extraSkills, extraPlans, extraMcp, extraComposio,
                    taskStateStore, hitlManager, knowledgeStore, agentRegistry, skillRegistry, planRegistry, llmDecorator,
                    taskDecorator, stepListener, taskExecutor);
        }
    }
}
