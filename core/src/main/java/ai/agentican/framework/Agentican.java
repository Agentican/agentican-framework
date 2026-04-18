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
import ai.agentican.framework.llm.BedrockLlmClient;
import ai.agentican.framework.llm.GeminiLlmClient;
import ai.agentican.framework.llm.LlmClient;
import ai.agentican.framework.llm.LlmClientDecorator;
import ai.agentican.framework.llm.OpenAiCompatibleLlmClient;
import ai.agentican.framework.llm.OpenAiLlmClient;
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
    private KnowledgeIngestor knowledgeIngestor;

    private final ExecutorService taskExecutor;
    private final boolean ownsExecutor;

    private final java.util.concurrent.CopyOnWriteArrayList<CompletableFuture<?>> reingestFutures
            = new java.util.concurrent.CopyOnWriteArrayList<>();

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

        this.llms = Collections.unmodifiableMap(mutableLlms);

        TaskStateStore notifyingStore = new NotifyingTaskStateStore(taskStateStore, this.taskListener);

        if (knowledgeStore != null) {

            var defaultLlmForKnowledge = this.llms.get(LlmConfig.DEFAULT);

            if (defaultLlmForKnowledge != null) {

                var extractor = new LlmKnowledgeExtractor(defaultLlmForKnowledge);
                var ingestor = new KnowledgeIngestor(taskStateStore, knowledgeStore, extractor, this.taskExecutor);
                this.knowledgeIngestor = ingestor;

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

    public TaskStateStore taskStateStore() {

        return taskStateStore;
    }

    public int reapOrphans() {

        return reapOrphans(ai.agentican.framework.orchestration.execution.resume.ReapReason.SERVER_RESTARTED);
    }

    public int resumeInterrupted() {

        return resumeInterrupted(10);
    }

    public int resumeInterrupted(int maxConcurrent) {

        var tasks = taskStateStore.listInProgress();
        int resumed = 0;
        int reaped = 0;
        var semaphore = new java.util.concurrent.Semaphore(maxConcurrent > 0 ? maxConcurrent : 1, true);

        for (var task : tasks) {

            if (task.status() != null) continue;
            if (task.parentTaskId() != null) continue;

            var plan = task.plan();

            var resumePlan = ai.agentican.framework.orchestration.execution.resume.ResumeClassifier
                    .classify(task, plan);

            if (resumePlan.reapOnly()) {

                LOG.warn("Task {} ({}) cannot be resumed: {} — reaping",
                        task.taskName(), task.taskId(),
                        resumePlan.reapReason() != null ? resumePlan.reapReason().name() : "UNKNOWN");

                reapSingleTask(task, resumePlan.reapReason() != null
                        ? resumePlan.reapReason()
                        : ai.agentican.framework.orchestration.execution.resume.ReapReason.UNKNOWN);
                reaped++;
                continue;
            }

            rehydratePendingCheckpoints(task);
            reingestCompletedSteps(task);

            LOG.info("Task {} ({}) resume classification: completedSteps={}, inFlightStep={}, turnState={}, pendingTools={} — "
                            + "submitting to executor",
                    task.taskName(), task.taskId(),
                    resumePlan.completedSteps().size(),
                    resumePlan.inFlightStep().map(s -> s.stepName()).orElse("<none>"),
                    resumePlan.turnState(),
                    resumePlan.toolsToExecute().size());

            var finalPlan = plan;
            var finalTaskId = task.taskId();
            var finalParams = task.params();

            var cancelled = new AtomicBoolean(false);

            var submitted = wrapTaskRunner(Mdc.propagate(() -> {
                try {
                    semaphore.acquire();
                    taskListener.onTaskResumed(finalTaskId);
                    return taskRunner.resume(finalPlan, finalTaskId, finalParams, cancelled);
                }
                catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    LOG.warn("Resume of task {} interrupted while waiting for concurrency slot", finalTaskId);
                    taskListener.onTaskCompleted(finalTaskId, TaskStatus.CANCELLED);
                    throw new java.util.concurrent.CompletionException(e);
                }
                catch (Exception e) {
                    LOG.error("Resume of task {} failed: {}", finalTaskId, e.getMessage(), e);
                    taskListener.onTaskCompleted(finalTaskId, TaskStatus.FAILED);
                    throw e;
                }
                finally {
                    semaphore.release();
                }
            }));

            CompletableFuture.supplyAsync(submitted, taskExecutor);

            resumed++;
        }

        var danglingReaped = reapDanglingSubTasks(tasks);

        if (resumed > 0 || reaped > 0 || danglingReaped > 0)
            LOG.info("Resume-on-start: {} task(s) resumed, {} task(s) reaped, {} dangling sub-task(s) cleaned",
                    resumed, reaped, danglingReaped);

        return resumed + reaped + danglingReaped;
    }

    private int reapDanglingSubTasks(List<ai.agentican.framework.state.TaskLog> inProgress) {

        int reaped = 0;
        for (var t : inProgress) {
            if (t.parentTaskId() == null) continue;

            var parent = taskStateStore.load(t.parentTaskId());
            if (parent == null || parent.status() == null) continue;

            LOG.warn("Reaping dangling sub-task {} (parent {} already terminal: {})",
                    t.taskId(), t.parentTaskId(), parent.status());
            reapSingleTask(t, ai.agentican.framework.orchestration.execution.resume.ReapReason.DANGLING_PARENT_TERMINAL);
            reaped++;
        }

        return reaped;
    }

    private void reingestCompletedSteps(ai.agentican.framework.state.TaskLog task) {

        if (knowledgeIngestor == null) return;

        var taskId = task.taskId();
        var stepIds = task.steps().values().stream()
                .filter(s -> s.status() == TaskStatus.COMPLETED)
                .filter(s -> s.output() != null && !s.output().isBlank())
                .map(ai.agentican.framework.state.StepLog::id)
                .toList();

        if (stepIds.isEmpty()) return;

        var future = CompletableFuture.runAsync(() -> {
            for (var stepId : stepIds) {
                try {
                    knowledgeIngestor.onStepCompleted(taskId, stepId);
                }
                catch (RuntimeException ex) {
                    LOG.warn("Knowledge re-ingestion for step {} of task {} failed: {}",
                            stepId, taskId, ex.getMessage());
                }
            }
        }, taskExecutor);

        reingestFutures.add(future);
        future.whenComplete((v, ex) -> reingestFutures.remove(future));
    }

    private void rehydratePendingCheckpoints(ai.agentican.framework.state.TaskLog task) {

        if (hitlManager == null) return;

        for (var step : task.steps().values()) {
            var checkpoint = step.checkpoint();
            if (checkpoint == null) continue;
            if (hitlManager.hasPending(checkpoint.id())) continue;

            hitlManager.rehydrate(checkpoint);

            var persistedResponse = step.hitlResponse();
            if (persistedResponse != null) {
                LOG.info("Rehydrated HITL checkpoint {} for task {} / step {}; replaying persisted response (approved={})",
                        checkpoint.id(), task.taskId(), step.stepName(), persistedResponse.approved());
                hitlManager.respond(checkpoint.id(), persistedResponse);
            }
            else {
                LOG.info("Rehydrated HITL checkpoint {} for task {} / step {}; awaiting human response",
                        checkpoint.id(), task.taskId(), step.stepName());
            }
        }
    }

    private void reapSingleTask(ai.agentican.framework.state.TaskLog task,
                                ai.agentican.framework.orchestration.execution.resume.ReapReason reason) {

        reapOrphanedSubTasks(task.taskId(), reason);

        for (var step : task.steps().values()) {
            if (step.status() == null)
                taskStateStore.stepCompleted(task.taskId(), step.id(), TaskStatus.FAILED,
                        "Step abandoned: " + reason.name());
        }
        taskStateStore.taskCompleted(task.taskId(), TaskStatus.FAILED);
        taskListener.onTaskReaped(task.taskId(), reason);
    }

    private void reapOrphanedSubTasks(String parentTaskId,
                                      ai.agentican.framework.orchestration.execution.resume.ReapReason reason) {

        var all = taskStateStore.list();
        for (var candidate : all) {
            if (!parentTaskId.equals(candidate.parentTaskId())) continue;
            if (candidate.status() != null) continue;

            reapOrphanedSubTasks(candidate.taskId(), reason);

            for (var step : candidate.steps().values()) {
                if (step.status() == null)
                    taskStateStore.stepCompleted(candidate.taskId(), step.id(), TaskStatus.FAILED,
                            "Step abandoned: " + reason.name());
            }
            taskStateStore.taskCompleted(candidate.taskId(), TaskStatus.FAILED);
            taskListener.onTaskReaped(candidate.taskId(),
                    ai.agentican.framework.orchestration.execution.resume.ReapReason.PARENT_REAPED);
        }
    }


    public int reapOrphans(ai.agentican.framework.orchestration.execution.resume.ReapReason reason) {

        var tasks = taskStateStore.listInProgress();
        int reaped = 0;

        for (var task : tasks) {

            if (task.status() != null) continue;
            if (task.parentTaskId() != null) continue;

            reapSingleTask(task, reason);

            LOG.warn("Reaped orphan task {} ({}): {}",
                    task.taskName() != null ? task.taskName() : task.taskId(), task.taskId(), reason.name());

            reaped++;
        }

        if (reaped > 0) LOG.info("Reaped {} orphan task(s) on startup", reaped);

        return reaped;
    }

    private static void requireExternalId(String kind, String name, String externalId) {

        if (externalId == null || externalId.isBlank())
            throw new IllegalStateException(kind + " '" + name + "' is missing an externalId. "
                    + "Config-file and fluent-builder " + kind + "s must declare a stable externalId "
                    + "so the catalog can upsert consistently across deploys.");
    }

    @Override
    public void close() {

        var pending = reingestFutures.toArray(new CompletableFuture[0]);
        if (pending.length > 0) {
            try {
                CompletableFuture.allOf(pending)
                        .get(10, java.util.concurrent.TimeUnit.SECONDS);
            }
            catch (java.util.concurrent.TimeoutException ex) {
                LOG.warn("Knowledge re-ingestion did not finish within 10s on close; {} job(s) abandoned",
                        pending.length);
            }
            catch (Exception ex) {
                LOG.warn("Knowledge re-ingestion wait interrupted on close: {}", ex.getMessage());
                Thread.currentThread().interrupt();
            }
        }

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
