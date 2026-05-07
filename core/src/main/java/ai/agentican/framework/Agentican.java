package ai.agentican.framework;

import ai.agentican.framework.agent.AgentFactory;
import ai.agentican.framework.orchestration.execution.*;
import ai.agentican.framework.registry.AgentRegistry;
import ai.agentican.framework.registry.AgentRegistryMemory;
import ai.agentican.framework.config.AgentConfig;
import ai.agentican.framework.config.ComposioConfig;
import ai.agentican.framework.config.LlmConfig;
import ai.agentican.framework.config.McpConfig;
import ai.agentican.framework.config.WorkflowConfig;
import ai.agentican.framework.config.CatalogConfig;
import ai.agentican.framework.config.EngineConfig;
import ai.agentican.framework.config.SkillConfig;
import ai.agentican.framework.config.WorkerConfig;
import ai.agentican.framework.hitl.HitlManager;
import ai.agentican.framework.hitl.HitlNotifier;
import ai.agentican.framework.vector.VectorIndex;
import ai.agentican.framework.vector.VectorIndexRegistry;
import ai.agentican.framework.knowledge.KnowledgeIngestor;
import ai.agentican.framework.vector.code.RetrieveCodeStep;
import ai.agentican.framework.vector.code.RetrieveOutput;
import ai.agentican.framework.vector.code.RetrieveQuery;
import ai.agentican.framework.tools.vector.RetrievalToolkit;
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
import ai.agentican.framework.orchestration.model.WorkflowStepCode;
import ai.agentican.framework.store.WorkflowRunStoreMemory;
import ai.agentican.framework.store.WorkflowRunStoreNotifying;
import ai.agentican.framework.store.WorkflowRunStore;
import ai.agentican.framework.registry.WorkflowRegistryMemory;
import ai.agentican.framework.registry.WorkflowRegistry;
import ai.agentican.framework.registry.SkillRegistryMemory;
import ai.agentican.framework.registry.SkillRegistry;
import ai.agentican.framework.orchestration.model.WorkflowDefinition;
import ai.agentican.framework.orchestration.planning.WorkflowPlannerAgent;
import ai.agentican.framework.orchestration.planning.WorkflowPlan;
import ai.agentican.framework.tools.Toolkit;
import ai.agentican.framework.registry.ToolkitRegistry;
import ai.agentican.framework.tools.composio.ComposioClient;
import ai.agentican.framework.tools.mcp.McpToolkit;
import ai.agentican.framework.util.Logs;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

public class Agentican implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(Agentican.class);

    private final WorkflowPlannerAgent workflowPlanner;
    private final WorkflowEngine workflowEngine;

    private Agentican(WorkflowPlannerAgent workflowPlanner, WorkflowEngine workflowEngine) {

        this.workflowPlanner = workflowPlanner;
        this.workflowEngine = workflowEngine;
    }

    public WorkflowPlan plan(String description) {

        return workflowPlanner.plan(description);
    }

    public WorkflowRun<String> run(String description) {

        var plan = plan(description);

        var definition = plan.definition();
        var inputs = plan.inputs();

        return workflowEngine.dispatch(definition, inputs, null, WorkflowRunResult::output);
    }

    public WorkflowBuilder workflow(String workflowName) {

        return new WorkflowBuilder(workflowEngine, workflowName);
    }

    public WorkflowBuilder workflow(WorkflowDefinition plan) {

        return new WorkflowBuilder(workflowEngine, plan);
    }

    public TaskBuilder task(String taskName) {

        return new TaskBuilder(workflowEngine, taskName);
    }

    public AgenticanRegistry registry() {

        return workflowEngine.registry();
    }

    public AgenticanRecovery recovery() {

        return new AgenticanRecovery(workflowEngine);
    }

    @Override
    public void close() {

        workflowEngine.close();
        workflowEngine.registry().toolkits().close();
    }

    public static Builder builder() {

        return new Builder();
    }

    public static final class Builder {

        private final Map<String, LlmClient> llms = new LinkedHashMap<>();
        private final Map<String, Toolkit> toolkits = new LinkedHashMap<>();
        private final CodeStepRegistry codeStepRegistry = new CodeStepRegistry();
        private final VectorIndexRegistry vectorIndexRegistry = new VectorIndexRegistry();

        private HitlManager hitlManager;
        private KnowledgeStore knowledgeStore;
        private AgentRegistry agentRegistry;
        private SkillRegistry skillRegistry;
        private WorkflowRegistry workflowRegistry;
        private LlmClientDecorator llmDecorator;
        private WorkflowRunDecorator workflowRunDecorator;
        private WorkflowRunListener workflowRunListener;
        private WorkflowRunStore workflowRunStore;
        private ExecutorService taskExecutor;

        private final Configuration configuration = new Configuration();
        private final Registry registry = new Registry();

        Builder() {}

        public Agentican build() {

            List<LlmConfig> llmConfigs;
            List<McpConfig> mcpConfigs;

            ComposioConfig composioConfig;
            WorkerConfig workerConfig;

            boolean strict;

            if (configuration.mode == ConfigurationMode.YAML) {

                var loaded = configuration.yaml.load();

                llmConfigs = new ArrayList<>(loaded.llm());
                mcpConfigs = new ArrayList<>(loaded.mcp());
                composioConfig = loaded.composio();
                workerConfig = loaded.agentRunner();
                strict = loaded.strict();
            }
            else {

                llmConfigs = configuration.api.llmConfigs;
                mcpConfigs = configuration.api.mcpConfigs;
                composioConfig = configuration.api.composioConfig;
                workerConfig = configuration.api.workerConfig;
                strict = configuration.api.strict;
            }

            List<AgentConfig> agentCfgs;
            List<SkillConfig> skillCfgs;
            List<WorkflowConfig> workflowCfgs;

            if (registry.mode == RegistryMode.YAML) {

                var loaded = registry.yaml.load();

                agentCfgs = loaded.agents();
                skillCfgs = loaded.skills();
                workflowCfgs = loaded.workflows();
            }
            else if (registry.mode == RegistryMode.API) {

                agentCfgs = registry.api.agents;
                skillCfgs = registry.api.skills;
                workflowCfgs = registry.api.workflows;
            }
            else {

                agentCfgs = List.of();
                skillCfgs = List.of();
                workflowCfgs = List.of();
            }

            if (llmConfigs.isEmpty() && llms.isEmpty())
                throw new IllegalStateException("At least one LLM is required (declare an LlmConfig or inject an LlmClient)");

            var hm = hitlManager != null ? hitlManager : new HitlManager(HitlNotifier.logging());
            var ks = knowledgeStore != null ? knowledgeStore : new KnowledgeStoreMemory();
            var tss = workflowRunStore != null ? workflowRunStore : new WorkflowRunStoreMemory();
            var tl = workflowRunListener != null ? workflowRunListener : new WorkflowRunListener() {};
            var ownsExecutor = (taskExecutor == null);
            var executor = taskExecutor != null ? taskExecutor : Executors.newVirtualThreadPerTaskExecutor();

            var agentRunnerConfig = workerConfig != null ? workerConfig : new WorkerConfig(0, null);

            var mutableLlms = new LinkedHashMap<String, LlmClient>();

            llmConfigs.forEach(llmConfig -> {

                LlmClient client = switch (llmConfig.provider()) {

                    case "anthropic" -> AnthropicLlmClient.create(llmConfig);
                    case "openai", "groq" -> OpenAiLlmClient.create(llmConfig);
                    case "gemini" -> GeminiLlmClient.create(llmConfig);
                    case "bedrock" -> BedrockLlmClient.create(llmConfig);
                    case "sambanova",
                         "together",
                         "fireworks",
                         "openai-compatible" -> OpenAiCompatibleLlmClient.create(llmConfig);
                    default -> throw new IllegalStateException("Unsupported LLM provider: " + llmConfig.provider());
                };

                if (llmDecorator != null) client = llmDecorator.decorate(llmConfig, client);

                client = new RetryingLlmClient(client, agentRunnerConfig.llmMaxRetries(),
                        agentRunnerConfig.llmRetryBaseDelay());

                mutableLlms.put(llmConfig.name(), client);
            });

            mutableLlms.putAll(llms);

            var llmClients = Collections.unmodifiableMap(mutableLlms);

            var notifyingStore = new WorkflowRunStoreNotifying(tss, tl);

            KnowledgeIngestor knowledgeIngestor = null;

            var defaultLlm = llmClients.get(LlmConfig.DEFAULT);

            if (defaultLlm != null) {

                var extractor = new LlmKnowledgeExtractor(defaultLlm);

                knowledgeIngestor = new KnowledgeIngestor(tss, ks, extractor, executor);
                notifyingStore = new WorkflowRunStoreNotifying(notifyingStore, knowledgeIngestor);
            }

            var finalTss = notifyingStore;

            var toolkitRegistry = new ToolkitRegistry();

            mcpConfigs.forEach(mcpConfig ->
                    toolkitRegistry.register(mcpConfig.slug(), McpToolkit.of(mcpConfig)));

            if (composioConfig != null && composioConfig.apiKey() != null) {

                var composioClient = ComposioClient.of(composioConfig.apiKey(), composioConfig.userId());

                composioClient.availableToolkits().forEach(tk -> toolkitRegistry.register(tk.slug(), tk));
            }

            toolkits.forEach(toolkitRegistry::register);

            if (!vectorIndexRegistry.isEmpty()) {

                if (toolkits.containsKey(RetrievalToolkit.SLUG))
                    throw new IllegalStateException(
                            "Toolkit slug '" + RetrievalToolkit.SLUG
                                    + "' is reserved when vector indexs are configured. "
                                    + "Don't register a custom toolkit under that slug.");

                toolkitRegistry.register(RetrievalToolkit.SLUG, new RetrievalToolkit(vectorIndexRegistry));

                if (codeStepRegistry.contains(RetrieveCodeStep.SLUG))
                    throw new IllegalStateException(
                            "Code-step slug '" + RetrieveCodeStep.SLUG
                                    + "' is reserved when vector indexs are configured. "
                                    + "Don't register a custom code step under that slug.");

                codeStepRegistry.register(
                        new CodeStepSpec<>(RetrieveCodeStep.SLUG, RetrieveCodeStep.DESCRIPTION, RetrieveQuery.class,
                                RetrieveOutput.class),
                        new RetrieveCodeStep(vectorIndexRegistry));
            }

            var sr = skillRegistry != null ? skillRegistry : new SkillRegistryMemory();

            sr.seed();

            skillCfgs.forEach(sr::register);

            var agentFactory = AgentFactory.builder()
                    .workerConfig(agentRunnerConfig)
                    .llmConfigs(llmConfigs)
                    .llms(llmClients)
                    .hitlManager(hm)
                    .knowledgeStore(ks)
                    .workflowRunStore(finalTss)
                    .skillRegistry(sr)
                    .workflowRunListener(tl)
                    .build();

            var ar = agentRegistry != null ? agentRegistry : new AgentRegistryMemory();

            ar.agentFactory(agentFactory::build);
            ar.seed();

            agentCfgs.forEach(ar::register);

            var pr = workflowRegistry != null ? workflowRegistry : new WorkflowRegistryMemory();

            pr.seed();

            workflowCfgs.forEach(workflowConfig -> {

                var plan = workflowConfig.toDefinition(codeStepRegistry);

                for (var step : plan.steps()) {

                    if (step instanceof WorkflowStepCode<?> code && !codeStepRegistry.contains(code.codeSlug())) {

                        throw new IllegalStateException("WorkflowDefinition '" + plan.name() + "' step '" + code.name()
                                + "' references unknown code step slug '" + code.codeSlug() + "'");
                    }
                }

                pr.register(plan);
            });

            var taskPlanner = new WorkflowPlannerAgent(defaultLlm, ar, toolkitRegistry, sr, pr, agentFactory::build, strict);

            var taskRunner = new WorkflowRunner(ar, hm, toolkitRegistry, finalTss, agentRunnerConfig.taskTimeout(),
                    agentRunnerConfig.maxStepRetries(), workflowRunDecorator, codeStepRegistry);

            LOG.info(Logs.AGENTICAN_INIT,
                    llmClients.size(), toolkitRegistry.slugs().size(), ar.asMap().size(), pr.asMap().size());

            var agenticanRegistry = new AgenticanRegistry(pr, ar, toolkitRegistry, sr, vectorIndexRegistry);

            var engine = new WorkflowEngine(agenticanRegistry, finalTss, tl, taskRunner, executor,
                    workflowRunDecorator, hm, knowledgeIngestor, ownsExecutor);

            return new Agentican(taskPlanner, engine);
        }

        public Configuration configuration() { return configuration; }
        public Registry registry() { return registry; }

        public Builder llm(String name, LlmClient llm) { llms.put(name, llm); return this; }
        public Builder toolkit(String slug, Toolkit toolkit) { toolkits.put(slug, toolkit); return this; }
        public Builder hitlManager(HitlManager hitlManager) { this.hitlManager = hitlManager; return this; }
        public Builder knowledgeStore(KnowledgeStore knowledgeStore) { this.knowledgeStore = knowledgeStore; return this; }
        public Builder agentRegistry(AgentRegistry agentRegistry) { this.agentRegistry = agentRegistry; return this; }
        public Builder skillRegistry(SkillRegistry skillRegistry) { this.skillRegistry = skillRegistry; return this; }
        public Builder workflowRegistry(WorkflowRegistry workflowRegistry) { this.workflowRegistry = workflowRegistry; return this; }
        public Builder llmDecorator(LlmClientDecorator llmDecorator) { this.llmDecorator = llmDecorator; return this; }
        public Builder workflowRunDecorator(WorkflowRunDecorator workflowRunDecorator) { this.workflowRunDecorator = workflowRunDecorator; return this; }
        public Builder workflowRunListener(WorkflowRunListener workflowRunListener) { this.workflowRunListener = workflowRunListener; return this; }
        public Builder workflowRunStore(WorkflowRunStore workflowRunStore) { this.workflowRunStore = workflowRunStore; return this; }
        public Builder taskExecutor(ExecutorService taskExecutor) { this.taskExecutor = taskExecutor; return this; }

        public Builder vectorIndex(VectorIndex kb) {

            vectorIndexRegistry.register(kb);

            return this;
        }

        public <I, O> Builder codeStep(String slug, Class<I> inputType, Class<O> outputType, CodeStep<I, O> executor) {

            codeStepRegistry.register(new CodeStepSpec<>(slug, null, inputType, outputType), executor);

            return this;
        }

        private static EngineConfig loadEngine(EngineConfig preloaded, Path path, String classpathResource) {

            if (preloaded != null) return preloaded;

            try {

                if (path != null) return EngineConfig.load(path);

                if (classpathResource != null) {

                    var cl = Thread.currentThread().getContextClassLoader();

                    if (cl == null) cl = Builder.class.getClassLoader();

                    try (var in = cl.getResourceAsStream(classpathResource)) {

                        if (in == null)
                            throw new IllegalStateException("YAML classpath resource not found: " + classpathResource);

                        return EngineConfig.load(in);
                    }
                }
            }
            catch (java.io.IOException e) {

                throw new java.io.UncheckedIOException(e);
            }

            throw new IllegalStateException(
                    "Configuration source set to YAML but no source provided — call .path(Path), "
                            + ".classpath(String), or .config(EngineConfig).");
        }

        private static CatalogConfig loadCatalog(CatalogConfig preloaded, Path path, String classpathResource) {

            if (preloaded != null) return preloaded;

            try {

                if (path != null) return CatalogConfig.load(path);

                if (classpathResource != null) {

                    var cl = Thread.currentThread().getContextClassLoader();

                    if (cl == null) cl = Builder.class.getClassLoader();

                    try (var in = cl.getResourceAsStream(classpathResource)) {

                        if (in == null)
                            throw new IllegalStateException("YAML classpath resource not found: " + classpathResource);

                        return CatalogConfig.load(in);
                    }
                }
            }
            catch (java.io.IOException e) {

                throw new java.io.UncheckedIOException(e);
            }

            throw new IllegalStateException(
                    "Registry source set to YAML but no source provided — call .path(Path), "
                            + ".classpath(String), or .config(CatalogConfig).");
        }

        private enum ConfigurationMode { API, YAML }
        private enum RegistryMode { API, YAML }

        public final class Configuration {

            private ConfigurationMode mode;

            private final Api api = new Api();
            private final Yaml yaml = new Yaml();

            Configuration() {}

            public Api api() {

                requireMode(ConfigurationMode.API);

                return api;
            }

            public Yaml yaml() {

                requireMode(ConfigurationMode.YAML);

                return yaml;
            }

            public Builder end() { return Builder.this; }

            private void requireMode(ConfigurationMode m) {

                if (mode != null && mode != m)
                    throw new IllegalStateException(
                            "Configuration source already set to " + mode + "; cannot also use " + m);

                mode = m;
            }

            public final class Api {

                private final List<LlmConfig> llmConfigs = new ArrayList<>();
                private final List<McpConfig> mcpConfigs = new ArrayList<>();

                private ComposioConfig composioConfig;
                private WorkerConfig workerConfig;

                private boolean strict;

                Api() {}

                public Api strict() { strict = true; return this; }

                public LlmEntry llm()           { return new LlmEntry(); }
                public McpEntry mcp()           { return new McpEntry(); }
                public ComposioEntry composio() { return new ComposioEntry(); }
                public WorkerEntry worker()     { return new WorkerEntry(); }

                public Builder end() { return Builder.this; }

                public final class LlmEntry {

                    private final LlmConfig.LlmConfigBuilder delegate = LlmConfig.builder();

                    LlmEntry() {}

                    public LlmEntry name(String name)               { delegate.name(name); return this; }
                    public LlmEntry provider(String provider)       { delegate.provider(provider); return this; }
                    public LlmEntry model(String model)             { delegate.model(model); return this; }
                    public LlmEntry apiKey(String apiKey)           { delegate.apiKey(apiKey); return this; }
                    public LlmEntry secretKey(String secretKey)     { delegate.secretKey(secretKey); return this; }
                    public LlmEntry region(String region)           { delegate.region(region); return this; }
                    public LlmEntry maxTokens(long maxTokens)       { delegate.maxTokens(maxTokens); return this; }
                    public LlmEntry temperature(Double temperature) { delegate.temperature(temperature); return this; }
                    public LlmEntry baseUrl(String baseUrl)         { delegate.baseUrl(baseUrl); return this; }

                    public Api end() { llmConfigs.add(delegate.build()); return Api.this; }
                }

                public final class McpEntry {

                    private final McpConfig.McpConfigBuilder delegate = McpConfig.builder();

                    McpEntry() {}

                    public McpEntry slug(String slug)                       { delegate.slug(slug); return this; }
                    public McpEntry name(String name)                       { delegate.name(name); return this; }
                    public McpEntry url(String url)                         { delegate.url(url); return this; }
                    public McpEntry queryParam(String key, String value)    { delegate.queryParam(key, value); return this; }
                    public McpEntry header(String key, String value)        { delegate.header(key, value); return this; }

                    public Api end() { mcpConfigs.add(delegate.build()); return Api.this; }
                }

                public final class ComposioEntry {

                    private final ComposioConfig.ComposioConfigBuilder delegate = ComposioConfig.builder();

                    ComposioEntry() {}

                    public ComposioEntry apiKey(String apiKey) { delegate.apiKey(apiKey); return this; }
                    public ComposioEntry userId(String userId) { delegate.userId(userId); return this; }

                    public Api end() { composioConfig = delegate.build(); return Api.this; }
                }

                public final class WorkerEntry {

                    private final WorkerConfig.WorkerConfigBuilder delegate = WorkerConfig.builder();

                    WorkerEntry() {}

                    public WorkerEntry maxTurns(int maxTurns)                       { delegate.maxTurns(maxTurns); return this; }
                    public WorkerEntry timeout(Duration timeout)                    { delegate.timeout(timeout); return this; }
                    public WorkerEntry taskTimeout(Duration taskTimeout)            { delegate.taskTimeout(taskTimeout); return this; }
                    public WorkerEntry maxStepRetries(int maxStepRetries)           { delegate.maxStepRetries(maxStepRetries); return this; }
                    public WorkerEntry llmMaxRetries(int llmMaxRetries)             { delegate.llmMaxRetries(llmMaxRetries); return this; }
                    public WorkerEntry llmRetryBaseDelay(Duration llmRetryBaseDelay) { delegate.llmRetryBaseDelay(llmRetryBaseDelay); return this; }

                    public Api end() { workerConfig = delegate.build(); return Api.this; }
                }
            }

            public final class Yaml {

                private Path path;
                private String classpathResource;
                private EngineConfig preloaded;

                Yaml() {}

                public Yaml path(Path path) { this.path = path; return this; }
                public Yaml classpath(String resource) { this.classpathResource = resource; return this; }
                public Yaml config(EngineConfig config) { this.preloaded = config; return this; }

                public Builder end() { return Builder.this; }

                EngineConfig load() {

                    return loadEngine(preloaded, path, classpathResource);
                }
            }
        }

        public final class Registry {

            private RegistryMode mode;

            private final Api api = new Api();
            private final Yaml yaml = new Yaml();

            Registry() {}

            public Api api() {

                requireMode(RegistryMode.API);

                return api;
            }

            public Yaml yaml() {

                requireMode(RegistryMode.YAML);

                return yaml;
            }

            public Builder end() { return Builder.this; }

            private void requireMode(RegistryMode m) {

                if (mode != null && mode != m)
                    throw new IllegalStateException(
                            "Registry source already set to " + mode + "; cannot also use " + m);

                mode = m;
            }

            public final class Api {

                private final List<AgentConfig> agents = new ArrayList<>();
                private final List<SkillConfig> skills = new ArrayList<>();
                private final List<WorkflowConfig> workflows = new ArrayList<>();

                Api() {}

                public AgentEntry agent()       { return new AgentEntry(); }
                public SkillEntry skill()       { return new SkillEntry(); }
                public WorkflowEntry workflow() { return new WorkflowEntry(); }

                public Builder end() { return Builder.this; }

                public final class AgentEntry {

                    private final AgentConfig.AgentConfigBuilder delegate = AgentConfig.builder();

                    AgentEntry() {}

                    public AgentEntry id(String id)                 { delegate.id(id); return this; }
                    public AgentEntry name(String name)             { delegate.name(name); return this; }
                    public AgentEntry role(String role)             { delegate.role(role); return this; }
                    public AgentEntry llm(String llm)               { delegate.llm(llm); return this; }
                    public AgentEntry runner(String runner)         { delegate.runner(runner); return this; }
                    public AgentEntry maxTurns(Integer maxTurns)    { delegate.maxTurns(maxTurns); return this; }
                    public AgentEntry timeout(Duration timeout)     { delegate.timeout(timeout); return this; }

                    public Api end() { agents.add(delegate.build()); return Api.this; }
                }

                public final class SkillEntry {

                    private final SkillConfig.SkillConfigBuilder delegate = SkillConfig.builder();

                    SkillEntry() {}

                    public SkillEntry id(String id)                         { delegate.id(id); return this; }
                    public SkillEntry name(String name)                     { delegate.name(name); return this; }
                    public SkillEntry instructions(String instructions)     { delegate.instructions(instructions); return this; }

                    public Api end() { skills.add(delegate.build()); return Api.this; }
                }

                public final class WorkflowEntry {

                    private final WorkflowConfig.WorkflowConfigBuilder delegate = WorkflowConfig.builder();

                    WorkflowEntry() {}

                    public WorkflowEntry id(String id)                          { delegate.id(id); return this; }
                    public WorkflowEntry name(String name)                      { delegate.name(name); return this; }
                    public WorkflowEntry description(String description)        { delegate.description(description); return this; }
                    public WorkflowEntry outputStep(String stepName)            { delegate.outputStep(stepName); return this; }

                    public ParamEntry param()       { return new ParamEntry(); }
                    public StepEntry step()         { return new StepEntry(); }
                    public LoopEntry loop()         { return new LoopEntry(); }
                    public BranchEntry branch()     { return new BranchEntry(); }

                    public Api end() { workflows.add(delegate.build()); return Api.this; }

                    public final class ParamEntry {

                        private final WorkflowConfig.WorkflowConfigBuilder.ParamEntry inner = delegate.param();

                        ParamEntry() {}

                        public ParamEntry name(String name)                 { inner.name(name); return this; }
                        public ParamEntry description(String description)   { inner.description(description); return this; }
                        public ParamEntry defaultValue(String defaultValue) { inner.defaultValue(defaultValue); return this; }
                        public ParamEntry required(boolean required)        { inner.required(required); return this; }

                        public WorkflowEntry end() { inner.end(); return WorkflowEntry.this; }
                    }

                    public final class StepEntry {

                        private final WorkflowConfig.StepEntry<WorkflowConfig.WorkflowConfigBuilder> inner = delegate.step();

                        StepEntry() {}

                        public StepEntry name(String name)              { inner.name(name); return this; }
                        public AgentStepEntry agent(String agent)       { return new AgentStepEntry(inner.agent(agent)); }
                        public CodeStepEntry code(String slug)          { return new CodeStepEntry(inner.code(slug)); }
                    }

                    public final class AgentStepEntry {

                        private final WorkflowConfig.AgentStepEntry<WorkflowConfig.WorkflowConfigBuilder> inner;

                        AgentStepEntry(WorkflowConfig.AgentStepEntry<WorkflowConfig.WorkflowConfigBuilder> inner) { this.inner = inner; }

                        public AgentStepEntry instructions(String instructions) { inner.instructions(instructions); return this; }
                        public AgentStepEntry dependencies(String... deps)      { inner.dependencies(deps); return this; }
                        public AgentStepEntry dependencies(List<String> deps)   { inner.dependencies(deps); return this; }
                        public AgentStepEntry skills(String... skills)          { inner.skills(skills); return this; }
                        public AgentStepEntry skills(List<String> skills)       { inner.skills(skills); return this; }
                        public AgentStepEntry tools(String... tools)            { inner.tools(tools); return this; }
                        public AgentStepEntry tools(List<String> tools)         { inner.tools(tools); return this; }
                        public AgentStepEntry hitl(boolean hitl)                { inner.hitl(hitl); return this; }
                        public AgentStepEntry hitl()                            { inner.hitl(); return this; }

                        public WorkflowEntry end() { inner.end(); return WorkflowEntry.this; }
                    }

                    public final class CodeStepEntry {

                        private final WorkflowConfig.CodeStepEntry<WorkflowConfig.WorkflowConfigBuilder> inner;

                        CodeStepEntry(WorkflowConfig.CodeStepEntry<WorkflowConfig.WorkflowConfigBuilder> inner) { this.inner = inner; }

                        public <I> CodeStepEntry input(I input)              { inner.input(input); return this; }
                        public CodeStepEntry dependencies(String... deps)    { inner.dependencies(deps); return this; }
                        public CodeStepEntry dependencies(List<String> deps) { inner.dependencies(deps); return this; }

                        public WorkflowEntry end() { inner.end(); return WorkflowEntry.this; }
                    }

                    public final class LoopEntry {

                        private final WorkflowConfig.WorkflowConfigBuilder.LoopEntry inner = delegate.loop();

                        LoopEntry() {}

                        public LoopEntry name(String name)                  { inner.name(name); return this; }
                        public LoopEntry over(String stepName)              { inner.over(stepName); return this; }
                        public LoopEntry dependencies(String... deps)       { inner.dependencies(deps); return this; }
                        public LoopEntry dependencies(List<String> deps)    { inner.dependencies(deps); return this; }
                        public LoopEntry hitl(boolean hitl)                 { inner.hitl(hitl); return this; }
                        public LoopEntry hitl()                             { inner.hitl(); return this; }

                        public LoopStepEntry step() { return new LoopStepEntry(inner.step()); }

                        public WorkflowEntry end() { inner.end(); return WorkflowEntry.this; }

                        public final class LoopStepEntry {

                            private final WorkflowConfig.StepEntry<WorkflowConfig.WorkflowConfigBuilder.LoopEntry> innerStep;

                            LoopStepEntry(WorkflowConfig.StepEntry<WorkflowConfig.WorkflowConfigBuilder.LoopEntry> innerStep) {
                                this.innerStep = innerStep;
                            }

                            public LoopStepEntry name(String name)                  { innerStep.name(name); return this; }
                            public LoopAgentStepEntry agent(String agent)           { return new LoopAgentStepEntry(innerStep.agent(agent)); }
                            public LoopCodeStepEntry code(String slug)              { return new LoopCodeStepEntry(innerStep.code(slug)); }
                        }

                        public final class LoopAgentStepEntry {

                            private final WorkflowConfig.AgentStepEntry<WorkflowConfig.WorkflowConfigBuilder.LoopEntry> innerStep;

                            LoopAgentStepEntry(WorkflowConfig.AgentStepEntry<WorkflowConfig.WorkflowConfigBuilder.LoopEntry> innerStep) {
                                this.innerStep = innerStep;
                            }

                            public LoopAgentStepEntry instructions(String instructions) { innerStep.instructions(instructions); return this; }
                            public LoopAgentStepEntry dependencies(String... deps)      { innerStep.dependencies(deps); return this; }
                            public LoopAgentStepEntry dependencies(List<String> deps)   { innerStep.dependencies(deps); return this; }
                            public LoopAgentStepEntry skills(String... skills)          { innerStep.skills(skills); return this; }
                            public LoopAgentStepEntry skills(List<String> skills)       { innerStep.skills(skills); return this; }
                            public LoopAgentStepEntry tools(String... tools)            { innerStep.tools(tools); return this; }
                            public LoopAgentStepEntry tools(List<String> tools)         { innerStep.tools(tools); return this; }
                            public LoopAgentStepEntry hitl(boolean hitl)                { innerStep.hitl(hitl); return this; }
                            public LoopAgentStepEntry hitl()                            { innerStep.hitl(); return this; }

                            public LoopEntry end() { innerStep.end(); return LoopEntry.this; }
                        }

                        public final class LoopCodeStepEntry {

                            private final WorkflowConfig.CodeStepEntry<WorkflowConfig.WorkflowConfigBuilder.LoopEntry> innerStep;

                            LoopCodeStepEntry(WorkflowConfig.CodeStepEntry<WorkflowConfig.WorkflowConfigBuilder.LoopEntry> innerStep) {
                                this.innerStep = innerStep;
                            }

                            public <I> LoopCodeStepEntry input(I input)              { innerStep.input(input); return this; }
                            public LoopCodeStepEntry dependencies(String... deps)    { innerStep.dependencies(deps); return this; }
                            public LoopCodeStepEntry dependencies(List<String> deps) { innerStep.dependencies(deps); return this; }

                            public LoopEntry end() { innerStep.end(); return LoopEntry.this; }
                        }
                    }

                    public final class BranchEntry {

                        private final WorkflowConfig.WorkflowConfigBuilder.BranchEntry inner = delegate.branch();

                        BranchEntry() {}

                        public BranchEntry name(String name)                { inner.name(name); return this; }
                        public BranchEntry from(String stepName)            { inner.from(stepName); return this; }
                        public BranchEntry defaultPath(String pathName)     { inner.defaultPath(pathName); return this; }
                        public BranchEntry dependencies(String... deps)     { inner.dependencies(deps); return this; }
                        public BranchEntry dependencies(List<String> deps)  { inner.dependencies(deps); return this; }
                        public BranchEntry hitl(boolean hitl)               { inner.hitl(hitl); return this; }
                        public BranchEntry hitl()                           { inner.hitl(); return this; }

                        public PathEntry path() { return new PathEntry(inner.path()); }

                        public WorkflowEntry end() { inner.end(); return WorkflowEntry.this; }

                        public final class PathEntry {

                            private final WorkflowConfig.WorkflowConfigBuilder.BranchEntry.PathEntry innerPath;

                            PathEntry(WorkflowConfig.WorkflowConfigBuilder.BranchEntry.PathEntry innerPath) { this.innerPath = innerPath; }

                            public PathEntry name(String name)              { innerPath.name(name); return this; }
                            public PathEntry agent(String agent)            { innerPath.agent(agent); return this; }
                            public PathEntry instructions(String instr)     { innerPath.instructions(instr); return this; }
                            public PathEntry tools(String... tools)         { innerPath.tools(tools); return this; }
                            public PathEntry tools(List<String> tools)      { innerPath.tools(tools); return this; }
                            public PathEntry skills(String... skills)       { innerPath.skills(skills); return this; }
                            public PathEntry skills(List<String> skills)    { innerPath.skills(skills); return this; }

                            public PathStepEntry step() { return new PathStepEntry(innerPath.step()); }

                            public BranchEntry end() { innerPath.end(); return BranchEntry.this; }

                            public final class PathStepEntry {

                                private final WorkflowConfig.StepEntry<WorkflowConfig.WorkflowConfigBuilder.BranchEntry.PathEntry> innerStep;

                                PathStepEntry(WorkflowConfig.StepEntry<WorkflowConfig.WorkflowConfigBuilder.BranchEntry.PathEntry> innerStep) {
                                    this.innerStep = innerStep;
                                }

                                public PathStepEntry name(String name)              { innerStep.name(name); return this; }
                                public PathAgentStepEntry agent(String agent)       { return new PathAgentStepEntry(innerStep.agent(agent)); }
                                public PathCodeStepEntry code(String slug)          { return new PathCodeStepEntry(innerStep.code(slug)); }
                            }

                            public final class PathAgentStepEntry {

                                private final WorkflowConfig.AgentStepEntry<WorkflowConfig.WorkflowConfigBuilder.BranchEntry.PathEntry> innerStep;

                                PathAgentStepEntry(WorkflowConfig.AgentStepEntry<WorkflowConfig.WorkflowConfigBuilder.BranchEntry.PathEntry> innerStep) {
                                    this.innerStep = innerStep;
                                }

                                public PathAgentStepEntry instructions(String instructions) { innerStep.instructions(instructions); return this; }
                                public PathAgentStepEntry dependencies(String... deps)      { innerStep.dependencies(deps); return this; }
                                public PathAgentStepEntry dependencies(List<String> deps)   { innerStep.dependencies(deps); return this; }
                                public PathAgentStepEntry skills(String... skills)          { innerStep.skills(skills); return this; }
                                public PathAgentStepEntry skills(List<String> skills)       { innerStep.skills(skills); return this; }
                                public PathAgentStepEntry tools(String... tools)            { innerStep.tools(tools); return this; }
                                public PathAgentStepEntry tools(List<String> tools)         { innerStep.tools(tools); return this; }
                                public PathAgentStepEntry hitl(boolean hitl)                { innerStep.hitl(hitl); return this; }
                                public PathAgentStepEntry hitl()                            { innerStep.hitl(); return this; }

                                public PathEntry end() { innerStep.end(); return PathEntry.this; }
                            }

                            public final class PathCodeStepEntry {

                                private final WorkflowConfig.CodeStepEntry<WorkflowConfig.WorkflowConfigBuilder.BranchEntry.PathEntry> innerStep;

                                PathCodeStepEntry(WorkflowConfig.CodeStepEntry<WorkflowConfig.WorkflowConfigBuilder.BranchEntry.PathEntry> innerStep) {
                                    this.innerStep = innerStep;
                                }

                                public <I> PathCodeStepEntry input(I input)              { innerStep.input(input); return this; }
                                public PathCodeStepEntry dependencies(String... deps)    { innerStep.dependencies(deps); return this; }
                                public PathCodeStepEntry dependencies(List<String> deps) { innerStep.dependencies(deps); return this; }

                                public PathEntry end() { innerStep.end(); return PathEntry.this; }
                            }
                        }
                    }
                }
            }

            public final class Yaml {

                private Path path;
                private String classpathResource;
                private CatalogConfig preloaded;

                Yaml() {}

                public Yaml path(Path path) { this.path = path; return this; }
                public Yaml classpath(String resource) { this.classpathResource = resource; return this; }
                public Yaml config(CatalogConfig config) { this.preloaded = config; return this; }

                public Builder end() { return Builder.this; }

                CatalogConfig load() {

                    return loadCatalog(preloaded, path, classpathResource);
                }
            }
        }
    }
}
