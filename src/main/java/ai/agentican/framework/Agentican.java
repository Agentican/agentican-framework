package ai.agentican.framework;

import ai.agentican.framework.agent.Agent;
import ai.agentican.framework.agent.AgentRegistry;
import ai.agentican.framework.agent.SmacAgentRunner;
import ai.agentican.framework.config.AgentConfig;
import ai.agentican.framework.config.LlmConfig;
import ai.agentican.framework.config.RuntimeConfig;
import ai.agentican.framework.hitl.AskQuestionToolkit;
import ai.agentican.framework.hitl.HitlManager;
import ai.agentican.framework.hitl.HitlNotifier;
import ai.agentican.framework.knowledge.KnowledgeStore;
import ai.agentican.framework.knowledge.MemKnowledgeStore;
import ai.agentican.framework.llm.AnthropicLlmClient;
import ai.agentican.framework.llm.LlmClient;
import ai.agentican.framework.llm.LlmClientDecorator;
import ai.agentican.framework.llm.RetryingLlmClient;
import ai.agentican.framework.state.MemTaskStateStore;
import ai.agentican.framework.state.NotifyingTaskStateStore;
import ai.agentican.framework.state.TaskStateStore;
import ai.agentican.framework.orchestration.PlanRegistry;
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

public class Agentican implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(Agentican.class);

    private final RuntimeConfig config;

    private final Map<String, LlmClient> llms;

    private final PlanRegistry planRegistry;
    private final AgentRegistry agentRegistry;
    private final ToolkitRegistry toolkitRegistry;

    private final TaskStateStore taskStateStore;

    private final PlannerAgent taskPlanner;
    private final TaskRunner taskRunner;
    private final TaskDecorator taskDecorator;
    private final TaskListener taskListener;
    private final HitlManager hitlManager;
    private final KnowledgeStore knowledgeStore;

    private final ExecutorService taskExecutor;
    private final boolean ownsExecutor;

    public static AgenticanBuilder builder() {

        return new AgenticanBuilder();
    }

    private Agentican(RuntimeConfig config, Map<String, LlmClient> llms, Map<String, Toolkit> toolkits,
                      TaskStateStore taskStateStore, HitlManager hitlManager, KnowledgeStore knowledgeStore,
                      LlmClientDecorator llmDecorator, TaskDecorator taskDecorator, TaskListener taskListener,
                      ExecutorService taskExecutor) {

        this.config = config;

        this.taskDecorator = taskDecorator;
        this.taskListener = taskListener != null ? taskListener : new TaskListener() {};
        this.hitlManager = hitlManager;
        this.knowledgeStore = knowledgeStore;

        this.ownsExecutor = (taskExecutor == null);
        this.taskExecutor = taskExecutor != null ? taskExecutor : Executors.newVirtualThreadPerTaskExecutor();

        // build llm clients
        var mutableLlms = new LinkedHashMap<String, LlmClient>();

        var agentRunnerConfig = config.agentRunner();

        config.llm().forEach(llmConfig -> {

            var client = AnthropicLlmClient.create(llmConfig);

            if (llmDecorator != null) client = llmDecorator.decorate(llmConfig, client);

            // Wrap with retry so all callers (agent runner, planner, fact extractor) get retries
            client = new RetryingLlmClient(client,
                    agentRunnerConfig.llmMaxRetries(), agentRunnerConfig.llmRetryBaseDelay());

            mutableLlms.put(llmConfig.name(), client);
        });

        mutableLlms.putAll(llms);

        this.llms = Collections.unmodifiableMap(mutableLlms);

        // build toolkits
        var toolkitRegistry = new ToolkitRegistry();

        // MCP
        config.mcp().forEach(mcpConfig ->
                toolkitRegistry.register(mcpConfig.slug(), McpToolkit.of(mcpConfig)));

        // Composio
        var composioConfig = config.composio();

        if (composioConfig != null && composioConfig.apiKey() != null) {

            var composioApiKey = composioConfig.apiKey();
            var composioUserId = composioConfig.userId();

            var composioClient = ComposioClient.of(composioApiKey, composioUserId);

            composioClient.availableToolkits().forEach(composioToolkit ->
                    toolkitRegistry.register(composioToolkit.slug(), composioToolkit));
        }

        // custom
        toolkits.forEach(toolkitRegistry::register);

        // built-in
        toolkitRegistry.register(AskQuestionToolkit.TOOL_NAME, new AskQuestionToolkit());

        this.toolkitRegistry = toolkitRegistry;

        this.taskStateStore = new NotifyingTaskStateStore(taskStateStore, this.taskListener);

        var defaultLlm = this.llms.get(LlmConfig.DEFAULT);

        // build agents
        var agentRegistry = new AgentRegistry();

        config.agents().forEach(agentConfig -> agentRegistry.register(buildAgent(agentConfig)));

        this.agentRegistry = agentRegistry;

        // build plans
        var planRegistry = new PlanRegistry();

        config.plans().forEach(planConfig -> planRegistry.register(planConfig.toPlan()));

        this.planRegistry = planRegistry;

        this.taskPlanner = new PlannerAgent(defaultLlm, agentRegistry, toolkitRegistry, this::buildAgent);

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

        var taskResult = CompletableFuture.supplyAsync(asyncTaskRunner, taskExecutor);

        return new TaskHandle(taskId, taskResult, taskCancelled);
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

                var plan = taskPlanner.plan(taskDescription);
                planRegistry.registerIfAbsent(plan);

                taskListener.onPlanCompleted(taskId, plan.id());

                return taskRunner.run(plan, taskId, Map.of(), taskCancelled);

            }
            catch (Exception e) {

                LOG.error("Task {} failed: {}", taskId, e.getMessage(), e);

                taskListener.onTaskCompleted(taskId,
                        TaskStatus.FAILED);

                throw e;
            }

        }));

        var taskResult = CompletableFuture.supplyAsync(asyncTaskRunner, taskExecutor);

        return new TaskHandle(taskId, taskResult, taskCancelled);
    }

    private <T> Supplier<T> wrapTaskRunner(Supplier<T> supplier) {

        return taskDecorator != null ? taskDecorator.decorate(supplier) : supplier;
    }

    public AgentRegistry agents() {

        return agentRegistry;
    }

    public PlanRegistry plans() {

        return planRegistry;
    }

    public HitlManager hitlManager() {

        return hitlManager;
    }

    public ToolkitRegistry toolkitRegistry() {

        return toolkitRegistry;
    }

    private Agent buildAgent(AgentConfig agentConfig) {

        var agentName = agentConfig.name();
        var agentRole = agentConfig.role();
        var agentSkills = agentConfig.skills();

        var llmName = agentConfig.llm();
        var defaultLlm = llms.get(LlmConfig.DEFAULT);

        var agentLlm = llms.getOrDefault(llmName, defaultLlm);

        if (agentLlm == null)
            throw new IllegalStateException("No LLM client found for '" + agentConfig.llm() + "' (agent: " + agentName + ")");

        var agentRunnerConfig = config.agentRunner();

        var maxTurns = agentRunnerConfig.maxTurns();
        var timeout = agentRunnerConfig.timeout();

        // Resolve LLM config for metadata (provider, model)
        var llmConfig = config.llm().stream()
                .filter(c -> c.name().equals(llmName))
                .findFirst()
                .orElse(config.llm().isEmpty() ? null : config.llm().getFirst());

        var agentRunnerBuilder = SmacAgentRunner.builder()
                .llmClient(agentLlm)
                .llmName(llmName != null ? llmName : LlmConfig.DEFAULT)
                .llmProvider(llmConfig != null ? llmConfig.provider() : null)
                .llmModel(llmConfig != null ? llmConfig.model() : null)
                .maxIterations(maxTurns)
                .timeout(timeout)
                .hitlManager(hitlManager)
                .knowledgeStore(knowledgeStore)
                .taskStateStore(taskStateStore)
                .taskListener(taskListener);

        var agentRunner = agentRunnerBuilder.build();

        var agent = new Agent(agentName, agentRole, agentSkills, agentRunner);

        LOG.info(Logs.AGENTICAN_BUILT_AGENT, agentName);

        return agent;
    }

    @Override
    public void close() {

        if (ownsExecutor) {
            taskExecutor.shutdownNow();
        }

        toolkitRegistry.close();
    }

    public static class AgenticanBuilder {

        private RuntimeConfig config;

        private final Map<String, LlmClient> llms = new LinkedHashMap<>();
        private final Map<String, Toolkit> toolkits = new LinkedHashMap<>();

        private TaskStateStore taskStateStore;
        private HitlManager hitlManager;
        private KnowledgeStore knowledgeStore;
        private LlmClientDecorator llmDecorator;
        private TaskDecorator taskDecorator;
        private TaskListener stepListener;
        private ExecutorService taskExecutor;

        public AgenticanBuilder config(RuntimeConfig config) { this.config = config; return this; }
        public AgenticanBuilder llm(String name, LlmClient llm) { this.llms.put(name, llm); return this; }
        public AgenticanBuilder toolkit(String slug, Toolkit toolkit) { this.toolkits.put(slug, toolkit); return this; }
        public AgenticanBuilder hitlManager(HitlManager hitlManager) { this.hitlManager = hitlManager; return this; }
        public AgenticanBuilder knowledgeStore(KnowledgeStore knowledgeStore) { this.knowledgeStore = knowledgeStore; return this; }
        public AgenticanBuilder taskStateStore(TaskStateStore taskStateStore) { this.taskStateStore = taskStateStore; return this; }
        public AgenticanBuilder llmDecorator(LlmClientDecorator llmDecorator) { this.llmDecorator = llmDecorator; return this; }
        public AgenticanBuilder taskDecorator(TaskDecorator taskDecorator) { this.taskDecorator = taskDecorator; return this; }
        public AgenticanBuilder stepListener(TaskListener stepListener) { this.stepListener = stepListener; return this; }
        public AgenticanBuilder taskExecutor(ExecutorService taskExecutor) { this.taskExecutor = taskExecutor; return this; }

        public Agentican build() {

            if (config == null)
                throw new IllegalStateException("RuntimeConfig is required");

            hitlManager = hitlManager != null ? hitlManager : new HitlManager(HitlNotifier.logging());
            knowledgeStore = knowledgeStore != null ? knowledgeStore : new MemKnowledgeStore();
            taskStateStore = taskStateStore != null ? taskStateStore : new MemTaskStateStore();

            return new Agentican(config, llms, toolkits, taskStateStore, hitlManager, knowledgeStore,
                    llmDecorator, taskDecorator, stepListener, taskExecutor);
        }
    }
}
