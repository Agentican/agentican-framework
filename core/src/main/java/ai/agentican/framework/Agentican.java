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
import ai.agentican.framework.config.RuntimeConfig;
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

            var config = new RuntimeConfig(llmConfigs, mcpConfigs, composioConfig, workerConfig, agentCfgs, skillCfgs,
                    workflowCfgs, strict);

            var hm = hitlManager != null ? hitlManager : new HitlManager(HitlNotifier.logging());
            var ks = knowledgeStore != null ? knowledgeStore : new KnowledgeStoreMemory();
            var tss = workflowRunStore != null ? workflowRunStore : new WorkflowRunStoreMemory();
            var tl = workflowRunListener != null ? workflowRunListener : new WorkflowRunListener() {};
            var ownsExecutor = (taskExecutor == null);
            var executor = taskExecutor != null ? taskExecutor : Executors.newVirtualThreadPerTaskExecutor();

            var agentRunnerConfig = config.agentRunner() != null
                    ? config.agentRunner()
                    : new WorkerConfig(0, null);

            var mutableLlms = new LinkedHashMap<String, LlmClient>();

            config.llm().forEach(llmConfig -> {

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

            config.mcp().forEach(mcpConfig ->
                    toolkitRegistry.register(mcpConfig.slug(), McpToolkit.of(mcpConfig)));

            var composioCfg = config.composio();

            if (composioCfg != null && composioCfg.apiKey() != null) {

                var composioClient = ComposioClient.of(composioCfg.apiKey(), composioCfg.userId());

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

            config.skills().forEach(sr::register);

            var agentFactory = AgentFactory.builder()
                    .config(config)
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

            config.agents().forEach(ar::register);

            var pr = workflowRegistry != null ? workflowRegistry : new WorkflowRegistryMemory();

            pr.seed();

            config.workflows().forEach(workflowConfig -> {

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

        private static RuntimeConfig loadYaml(RuntimeConfig preloaded, Path path, String classpathResource,
                                              String missingMessage) {

            if (preloaded != null) return preloaded;

            if (path != null) {

                try {

                    return RuntimeConfig.load(path);
                }
                catch (java.io.IOException e) {

                    throw new java.io.UncheckedIOException(e);
                }
            }

            if (classpathResource != null) {

                var cl = Thread.currentThread().getContextClassLoader();

                if (cl == null) cl = Builder.class.getClassLoader();

                try (var in = cl.getResourceAsStream(classpathResource)) {

                    if (in == null)
                        throw new IllegalStateException("YAML classpath resource not found: " + classpathResource);

                    return RuntimeConfig.load(in);
                }
                catch (java.io.IOException e) {

                    throw new java.io.UncheckedIOException(e);
                }
            }

            throw new IllegalStateException(missingMessage);
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

                public Api llm(LlmConfig llm) { llmConfigs.add(llm); return this; }
                public Api mcp(McpConfig mcp) { mcpConfigs.add(mcp); return this; }
                public Api composio(ComposioConfig composio) { composioConfig = composio; return this; }
                public Api worker(WorkerConfig worker) { workerConfig = worker; return this; }
                public Api strict() { strict = true; return this; }

                public Builder end() { return Builder.this; }
            }

            public final class Yaml {

                private Path path;
                private String classpathResource;
                private RuntimeConfig preloaded;

                Yaml() {}

                public Yaml path(Path path) { this.path = path; return this; }
                public Yaml classpath(String resource) { this.classpathResource = resource; return this; }
                public Yaml config(RuntimeConfig config) { this.preloaded = config; return this; }

                public Builder end() { return Builder.this; }

                RuntimeConfig load() {
                    return loadYaml(preloaded, path, classpathResource,
                            "Configuration source set to YAML but no source provided — call .path(Path), "
                                    + ".classpath(String), or .config(RuntimeConfig).");
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

                public Api agent(AgentConfig agent)             { agents.add(agent);       return this; }
                public Api skill(SkillConfig skill)             { skills.add(skill);       return this; }
                public Api workflow(WorkflowConfig workflow)    { workflows.add(workflow); return this; }

                public Builder end() { return Builder.this; }
            }

            public final class Yaml {

                private Path path;
                private String classpathResource;
                private RuntimeConfig preloaded;

                Yaml() {}

                public Yaml path(Path path) { this.path = path; return this; }
                public Yaml classpath(String resource) { this.classpathResource = resource; return this; }
                public Yaml config(RuntimeConfig config) { this.preloaded = config; return this; }

                public Builder end() { return Builder.this; }

                RuntimeConfig load() {
                    return loadYaml(preloaded, path, classpathResource,
                            "Registry source set to YAML but no source provided — call .path(Path), "
                                    + ".classpath(String), or .config(RuntimeConfig).");
                }
            }
        }
    }
}
