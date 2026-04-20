package ai.agentican.framework;

import ai.agentican.framework.agent.AgentFactory;
import ai.agentican.framework.registry.AgentRegistry;
import ai.agentican.framework.invoker.Agentican;
import ai.agentican.framework.registry.AgentRegistryMemory;
import ai.agentican.framework.config.AgentConfig;
import ai.agentican.framework.config.ComposioConfig;
import ai.agentican.framework.config.LlmConfig;
import ai.agentican.framework.config.McpConfig;
import ai.agentican.framework.config.PlanConfig;
import ai.agentican.framework.config.RuntimeConfig;
import ai.agentican.framework.config.SkillConfig;
import ai.agentican.framework.config.WorkerConfig;
import ai.agentican.framework.hitl.HitlManager;
import ai.agentican.framework.hitl.HitlNotifier;
import ai.agentican.framework.knowledge.KnowledgeIngestor;
import ai.agentican.framework.store.KnowledgeStore;
import ai.agentican.framework.knowledge.LlmKnowledgeExtractor;
import ai.agentican.framework.store.KnowledgeStoreMemory;
import ai.agentican.framework.llm.provider.AnthropicLlmClient;
import ai.agentican.framework.llm.provider.BedrockLlmClient;
import ai.agentican.framework.llm.provider.GeminiLlmClient;
import ai.agentican.framework.llm.LlmClient;
import ai.agentican.framework.llm.LlmClientDecorator;
import ai.agentican.framework.llm.provider.OpenAiCompatibleLlmClient;
import ai.agentican.framework.llm.provider.OpenAiLlmClient;
import ai.agentican.framework.llm.RetryingLlmClient;
import ai.agentican.framework.orchestration.code.CodeStep;
import ai.agentican.framework.orchestration.code.CodeStepRegistry;
import ai.agentican.framework.orchestration.code.CodeStepSpec;
import ai.agentican.framework.orchestration.execution.TaskResult;
import ai.agentican.framework.orchestration.model.PlanStepCode;
import ai.agentican.framework.store.TaskStateStoreMemory;
import ai.agentican.framework.store.TaskStateStoreNotifying;
import ai.agentican.framework.store.TaskStateStore;
import ai.agentican.framework.registry.PlanRegistryMemory;
import ai.agentican.framework.registry.PlanRegistry;
import ai.agentican.framework.registry.SkillRegistryMemory;
import ai.agentican.framework.registry.SkillRegistry;
import ai.agentican.framework.orchestration.execution.TaskHandle;
import ai.agentican.framework.orchestration.execution.TaskRunner;
import ai.agentican.framework.orchestration.execution.TaskStatus;
import ai.agentican.framework.util.Ids;
import ai.agentican.framework.util.Mdc;
import ai.agentican.framework.orchestration.model.Plan;
import ai.agentican.framework.orchestration.planning.PlannerAgent;
import ai.agentican.framework.tools.Toolkit;
import ai.agentican.framework.registry.ToolkitRegistry;
import ai.agentican.framework.tools.composio.ComposioClient;
import ai.agentican.framework.tools.mcp.McpToolkit;
import ai.agentican.framework.util.Logs;
import ai.agentican.framework.orchestration.execution.TaskDecorator;
import ai.agentican.framework.orchestration.execution.TaskListener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiFunction;
import java.util.function.Supplier;

public class AgenticanRuntime implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(AgenticanRuntime.class);

    private final AgenticanRegistry registry;

    private final PlannerAgent taskPlanner;
    private final TaskRunner taskRunner;
    private final TaskDecorator taskDecorator;
    private final TaskListener taskListener;
    private final TaskStateStore taskStateStore;

    private final HitlManager hitlManager;

    private final KnowledgeIngestor knowledgeIngestor;

    private final ExecutorService taskExecutor;
    private final boolean ownsExecutor;

    private AgenticanRuntime(AgenticanRegistry registry, PlannerAgent taskPlanner, TaskRunner taskRunner,
                      TaskStateStore taskStateStore, HitlManager hitlManager, KnowledgeIngestor knowledgeIngestor,
                      TaskDecorator taskDecorator, TaskListener taskListener, ExecutorService taskExecutor,
                      boolean ownsExecutor) {

        this.registry = registry;
        this.taskPlanner = taskPlanner;
        this.taskRunner = taskRunner;
        this.taskStateStore = taskStateStore;
        this.hitlManager = hitlManager;
        this.knowledgeIngestor = knowledgeIngestor;
        this.taskDecorator = taskDecorator;
        this.taskListener = taskListener;
        this.taskExecutor = taskExecutor;
        this.ownsExecutor = ownsExecutor;
    }

    public TaskHandle run(String taskDescription) {

        return submit((taskId, cancelled) -> {

            MDC.put("taskId", "[" + taskId + "] ");

            LOG.info(Logs.AGENTICAN_DEL_TASK);
            LOG.debug(Logs.AGENTICAN_DEL_TASK_FULL, taskDescription);

            taskListener.onPlanStarted(taskId);

            var planningResult = taskPlanner.plan(taskDescription);
            var plan = planningResult.plan();

            registry.plans().registerIfAbsent(plan);
            taskListener.onPlanCompleted(taskId, plan.id());

            return taskRunner.run(plan, taskId, planningResult.inputs(), cancelled);
        });
    }

    public TaskHandle run(Plan plan) {

        return run(plan, Map.of());
    }

    public TaskHandle run(Plan plan, Map<String, String> taskInputs) {

        return run(plan, taskInputs, null);
    }

    public TaskHandle run(Plan plan, Map<String, String> taskInputs,
                          ai.agentican.framework.orchestration.execution.OutputBinding outputBinding) {

        return submit((taskId, cancelled) -> {

            registry.plans().registerIfAbsent(plan);
            taskListener.onPlanCompleted(taskId, plan.id());

            return taskRunner.run(plan, taskId, taskInputs, cancelled, outputBinding);
        });
    }

    public AgenticanRegistry registry() {

        return registry;
    }

    public <P, R> Agentican<P, R> agentican(Plan plan, Class<P> paramsType, Class<R> outputType) {

        return Agentican.forPlan(this, plan, paramsType, outputType);
    }

    public <P, R> Agentican<P, R> agentican(String planName, Class<P> paramsType, Class<R> outputType) {

        return Agentican.byName(this, planName, paramsType, outputType);
    }

    public <P> Agentican<P, Void> agentican(Plan plan, Class<P> paramsType) {

        return agentican(plan, paramsType, Void.class);
    }

    public <P> Agentican<P, Void> agentican(String planName, Class<P> paramsType) {

        return agentican(planName, paramsType, Void.class);
    }

    @Override
    public void close() {

        if (ownsExecutor)
            taskExecutor.shutdownNow();

        registry.toolkits().close();
    }

    AgenticanInternals internals() {

        return new AgenticanInternals(taskStateStore, taskListener, taskRunner, taskExecutor, taskDecorator,
                hitlManager, knowledgeIngestor);
    }

    private TaskHandle submit(BiFunction<String, AtomicBoolean, TaskResult> taskFunnerFn) {

        var taskId = Ids.generate();
        var cancelled = new AtomicBoolean(false);

        var supplier = wrapTaskRunner(Mdc.propagate(() -> {

            try {

                return taskFunnerFn.apply(taskId, cancelled);
            }
            catch (Exception e) {

                LOG.error("Task {} failed: {}", taskId, e.getMessage(), e);
                taskListener.onTaskCompleted(taskId, TaskStatus.FAILED);
                throw e;
            }
        }));

        return new TaskHandle(taskId, CompletableFuture.supplyAsync(supplier, taskExecutor), cancelled);
    }

    private <T> Supplier<T> wrapTaskRunner(Supplier<T> supplier) {

        return taskDecorator != null ? taskDecorator.decorate(supplier) : supplier;
    }

    private static void requireExternalId(String kind, String name, String externalId) {

        if (externalId == null || externalId.isBlank())
            throw new IllegalStateException(kind + " '" + name + "' is missing an externalId. "
                    + "Config-file and fluent-builder " + kind + "s must declare a stable externalId "
                    + "so the catalog can upsert consistently across deploys.");
    }

    private static void validateCodeStepSlugs(Plan plan, CodeStepRegistry registry) {

        for (var step : plan.steps()) {

            if (step instanceof PlanStepCode<?> code && !registry.contains(code.codeSlug())) {

                throw new IllegalStateException("Plan '" + plan.name() + "' step '" + code.name()
                        + "' references unknown code step slug '" + code.codeSlug() + "'");
            }
        }
    }

    public static AgenticanBuilder builder() {

        return new AgenticanBuilder();
    }

    public static AgenticanBuilder builder(RuntimeConfig config) {

        return new AgenticanBuilder(config);
    }

    public static AgenticanBuilder builder(Path yamlConfigFile) throws java.io.IOException {

        return new AgenticanBuilder(RuntimeConfig.load(yamlConfigFile));
    }

    public static class AgenticanBuilder {

        private final List<LlmConfig> llmConfigs = new ArrayList<>();
        private final List<McpConfig> mcpConfigs = new ArrayList<>();
        private final List<AgentConfig> agentConfigs = new ArrayList<>();
        private final List<SkillConfig> skillConfigs = new ArrayList<>();
        private final List<PlanConfig> planConfigs = new ArrayList<>();
        private ComposioConfig composioConfig;
        private WorkerConfig workerConfig;

        private final Map<String, LlmClient> llms = new LinkedHashMap<>();
        private final Map<String, Toolkit> toolkits = new LinkedHashMap<>();
        private final CodeStepRegistry codeStepRegistry = new CodeStepRegistry();

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

        AgenticanBuilder() {}

        AgenticanBuilder(RuntimeConfig seed) {

            if (seed != null) {
                llmConfigs.addAll(seed.llm());
                mcpConfigs.addAll(seed.mcp());
                agentConfigs.addAll(seed.agents());
                skillConfigs.addAll(seed.skills());
                planConfigs.addAll(seed.plans());
                composioConfig = seed.composio();
                workerConfig = seed.agentRunner();
            }
        }

        public AgenticanBuilder llm(LlmConfig llm) { llmConfigs.add(llm); return this; }
        public AgenticanBuilder mcp(McpConfig mcp) { mcpConfigs.add(mcp); return this; }
        public AgenticanBuilder agent(AgentConfig agent) { agentConfigs.add(agent); return this; }
        public AgenticanBuilder skill(SkillConfig skill) { skillConfigs.add(skill); return this; }
        public AgenticanBuilder plan(PlanConfig plan) { planConfigs.add(plan); return this; }
        public AgenticanBuilder composio(ComposioConfig composio) { composioConfig = composio; return this; }
        public AgenticanBuilder worker(WorkerConfig worker) { workerConfig = worker; return this; }

        public AgenticanBuilder llm(String name, LlmClient llm) { llms.put(name, llm); return this; }
        public AgenticanBuilder toolkit(String slug, Toolkit toolkit) { toolkits.put(slug, toolkit); return this; }

        public <I, O> AgenticanBuilder codeStep(CodeStepSpec<I, O> spec, CodeStep<I, O> executor) {
            codeStepRegistry.register(spec, executor);
            return this;
        }

        public AgenticanBuilder hitlManager(HitlManager hitlManager) { this.hitlManager = hitlManager; return this; }
        public AgenticanBuilder knowledgeStore(KnowledgeStore knowledgeStore) { this.knowledgeStore = knowledgeStore; return this; }
        public AgenticanBuilder agentRegistry(AgentRegistry agentRegistry) { this.agentRegistry = agentRegistry; return this; }
        public AgenticanBuilder skillRegistry(SkillRegistry skillRegistry) { this.skillRegistry = skillRegistry; return this; }
        public AgenticanBuilder planRegistry(PlanRegistry planRegistry) { this.planRegistry = planRegistry; return this; }
        public AgenticanBuilder llmDecorator(LlmClientDecorator llmDecorator) { this.llmDecorator = llmDecorator; return this; }
        public AgenticanBuilder taskDecorator(TaskDecorator taskDecorator) { this.taskDecorator = taskDecorator; return this; }
        public AgenticanBuilder stepListener(TaskListener taskListener) { this.stepListener = taskListener; return this; }
        public AgenticanBuilder taskStateStore(TaskStateStore taskStateStore) { this.taskStateStore = taskStateStore; return this; }
        public AgenticanBuilder taskExecutor(ExecutorService taskExecutor) { this.taskExecutor = taskExecutor; return this; }

        public AgenticanRuntime build() {

            if (llmConfigs.isEmpty() && llms.isEmpty())
                throw new IllegalStateException("At least one LLM is required (declare an LlmConfig or inject an LlmClient)");

            var config = new RuntimeConfig(llmConfigs, mcpConfigs, composioConfig, workerConfig,
                    agentConfigs, skillConfigs, planConfigs);

            var hm = hitlManager != null ? hitlManager : new HitlManager(HitlNotifier.logging());
            var ks = knowledgeStore != null ? knowledgeStore : new KnowledgeStoreMemory();
            var tss = taskStateStore != null ? taskStateStore : new TaskStateStoreMemory();
            TaskListener tl = stepListener != null ? stepListener : new TaskListener() {};
            var ownsExecutor = (taskExecutor == null);
            var executor = taskExecutor != null ? taskExecutor : Executors.newVirtualThreadPerTaskExecutor();

            var agentRunnerConfig = config.agentRunner();

            var mutableLlms = new LinkedHashMap<String, LlmClient>();

            config.llm().forEach(llmConfig -> {

                LlmClient client = switch (llmConfig.provider()) {
                    case "anthropic"          -> AnthropicLlmClient.create(llmConfig);
                    case "openai", "groq"     -> OpenAiLlmClient.create(llmConfig);
                    case "gemini"             -> GeminiLlmClient.create(llmConfig);
                    case "bedrock"            -> BedrockLlmClient.create(llmConfig);
                    case "sambanova",
                         "together",
                         "fireworks",
                         "openai-compatible"  -> OpenAiCompatibleLlmClient.create(llmConfig);
                    default -> throw new IllegalStateException(
                            "Unsupported LLM provider: " + llmConfig.provider());
                };

                if (llmDecorator != null) client = llmDecorator.decorate(llmConfig, client);

                client = new RetryingLlmClient(client,
                        agentRunnerConfig.llmMaxRetries(), agentRunnerConfig.llmRetryBaseDelay());

                mutableLlms.put(llmConfig.name(), client);
            });

            mutableLlms.putAll(llms);

            var llmClients = Collections.unmodifiableMap(mutableLlms);

            TaskStateStore notifyingStore = new TaskStateStoreNotifying(tss, tl);
            KnowledgeIngestor knowledgeIngestor = null;

            var defaultLlm = llmClients.get(LlmConfig.DEFAULT);

            if (defaultLlm != null) {

                var extractor = new LlmKnowledgeExtractor(defaultLlm);
                knowledgeIngestor = new KnowledgeIngestor(tss, ks, extractor, executor);
                notifyingStore = new TaskStateStoreNotifying(notifyingStore, knowledgeIngestor);
            }

            var finalTss = notifyingStore;

            var toolkitRegistry = new ToolkitRegistry();

            config.mcp().forEach(mcpConfig ->
                    toolkitRegistry.register(mcpConfig.slug(), McpToolkit.of(mcpConfig)));

            var composioCfg = config.composio();

            if (composioCfg != null && composioCfg.apiKey() != null) {

                var composioClient = ComposioClient.of(composioCfg.apiKey(), composioCfg.userId());

                composioClient.availableToolkits().forEach(tk ->
                        toolkitRegistry.register(tk.slug(), tk));
            }

            toolkits.forEach(toolkitRegistry::register);

            SkillRegistry sr = skillRegistry != null ? skillRegistry : new SkillRegistryMemory();
            sr.seed();

            config.skills().forEach(skill -> {
                requireExternalId("skill", skill.name(), skill.externalId());
                sr.register(skill);
            });

            var agentFactory = AgentFactory.builder()
                    .config(config)
                    .llms(llmClients)
                    .hitlManager(hm)
                    .knowledgeStore(ks)
                    .taskStateStore(finalTss)
                    .skillRegistry(sr)
                    .taskListener(tl)
                    .build();

            AgentRegistry ar = agentRegistry != null ? agentRegistry : new AgentRegistryMemory();
            ar.seed(agentFactory::build);

            config.agents().forEach(agentConfig -> {
                requireExternalId("agent", agentConfig.name(), agentConfig.externalId());
                ar.register(agentFactory.build(agentConfig));
            });

            PlanRegistry pr = planRegistry != null ? planRegistry : new PlanRegistryMemory();
            pr.seed();

            config.plans().forEach(planConfig -> {
                requireExternalId("plan", planConfig.name(), planConfig.externalId());
                var plan = planConfig.toPlan(codeStepRegistry);
                validateCodeStepSlugs(plan, codeStepRegistry);
                pr.register(plan);
            });

            var taskPlanner = new PlannerAgent(defaultLlm, ar, toolkitRegistry, sr, pr, agentFactory::build);

            var taskRunner = new TaskRunner(ar, hm, toolkitRegistry, finalTss,
                    agentRunnerConfig.taskTimeout(), agentRunnerConfig.maxStepRetries(),
                    taskDecorator, codeStepRegistry);

            LOG.info(Logs.AGENTICAN_INIT,
                    llmClients.size(), toolkitRegistry.slugs().size(), ar.asMap().size(), pr.asMap().size());

            var registry = new AgenticanRegistry(pr, ar, toolkitRegistry, sr);

            return new AgenticanRuntime(registry, taskPlanner, taskRunner, finalTss, hm,
                    knowledgeIngestor, taskDecorator, tl, executor, ownsExecutor);
        }
    }
}
