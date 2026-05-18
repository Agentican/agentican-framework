package ai.agentican.quarkus;

import ai.agentican.framework.Agentican;
import ai.agentican.framework.AgenticanRecovery;
import ai.agentican.framework.registry.AgentRegistry;
import ai.agentican.framework.hitl.HitlManager;
import ai.agentican.framework.store.KnowledgeStore;
import ai.agentican.framework.registry.WorkflowRegistry;
import ai.agentican.framework.registry.SkillRegistry;
import ai.agentican.framework.orchestration.execution.WorkflowRunDecorator;
import ai.agentican.framework.llm.LlmClient;
import ai.agentican.framework.llm.LlmClientDecorator;
import ai.agentican.framework.store.WorkflowRunStore;
import ai.agentican.framework.tools.Toolkit;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Disposes;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Produces;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.inject.Inject;
import ai.agentican.framework.config.CatalogConfig;
import ai.agentican.framework.config.EngineConfig;
import ai.agentican.framework.config.LlmConfig;
import ai.agentican.framework.event.AgenticanEventListener;

@ApplicationScoped
public class AgenticanProducer {

    @Inject
    AgenticanConfig config;

    @Inject
    HitlManager hitlManager;

    @Inject
    KnowledgeStore knowledgeStore;

    @Inject
    WorkflowRunStore workflowRunStore;

    @Inject
    AgentRegistry agentRegistry;

    @Inject
    SkillRegistry skillRegistry;

    @Inject
    WorkflowRegistry workflowRegistry;

    @Inject
    Instance<LlmClient> llmClients;

    @Inject
    Instance<Toolkit> toolkits;

    @Inject
    Instance<LlmClientDecorator> llmDecorators;

    @Inject
    Instance<WorkflowRunDecorator> taskDecorators;

    @Inject
    Instance<AgenticanEventListener> stepListeners;

    @Inject
    Instance<java.util.concurrent.ExecutorService> taskExecutors;

    @Produces
    @jakarta.inject.Singleton
    public EngineConfig engineConfig() {

        var fromProps = EngineConfigConverter.fromProperties(config);
        var fromYaml = loadEngineYaml(config.engineConfig());

        return fromYaml != null ? EngineConfigConverter.merge(fromYaml, fromProps) : fromProps;
    }

    @Produces
    @jakarta.inject.Singleton
    public CatalogConfig catalogConfig() {

        return switch (config.catalogSource()) {
            case yaml -> loadCatalogYaml(config.catalogConfig());
            case database -> new CatalogConfig(
                    java.util.List.of(), java.util.List.of(), java.util.List.of());
        };
    }

    private static EngineConfig loadEngineYaml(String resourcePath) {

        try (var in = openClasspath(resourcePath)) {

            if (in == null) return null; // engine YAML is optional
            return EngineConfig.load(in);
        }
        catch (java.io.IOException e) {
            throw new IllegalStateException("Failed to load engine config from " + resourcePath, e);
        }
    }

    private static CatalogConfig loadCatalogYaml(String resourcePath) {

        try (var in = openClasspath(resourcePath)) {

            if (in == null)
                throw new IllegalStateException(
                        "agentican.catalog-config not found on classpath: " + resourcePath
                                + ". Place a CatalogConfig YAML at src/main/resources/" + resourcePath
                                + ", or set agentican.catalog-source=database.");

            return CatalogConfig.load(in);
        }
        catch (java.io.IOException e) {
            throw new IllegalStateException("Failed to load catalog config from " + resourcePath, e);
        }
    }

    private static java.io.InputStream openClasspath(String resourcePath) {

        var classloader = Thread.currentThread().getContextClassLoader();
        if (classloader == null) classloader = AgenticanProducer.class.getClassLoader();
        return classloader.getResourceAsStream(resourcePath);
    }

    @Produces
    @ApplicationScoped
    @io.quarkus.runtime.Startup
    public Agentican agentican(EngineConfig engineConfig,
                               CatalogConfig catalogConfig) {

        Agentican.Builder builder = Agentican.builder()
                .configuration().yaml().config(engineConfig).end();

        if (config.catalogSource() == AgenticanConfig.CatalogSource.yaml) {
            builder.registry().yaml().config(catalogConfig).end();
        }

        builder.hitlManager(hitlManager);
        builder.knowledgeStore(knowledgeStore);
        builder.workflowRunStore(workflowRunStore);

        if (config.catalogSource() == AgenticanConfig.CatalogSource.database) {
            builder.agentRegistry(agentRegistry);
            builder.skillRegistry(skillRegistry);
            builder.workflowRegistry(workflowRegistry);
        }

        var llmDecoratorList = llmDecorators.stream().toList();
        if (!llmDecoratorList.isEmpty()) {
            builder.llmDecorator((cfg, client) -> {
                var result = client;
                for (var d : llmDecoratorList) result = d.decorate(cfg, result);
                return result;
            });
        }

        var taskDecoratorList = taskDecorators.stream().toList();
        if (!taskDecoratorList.isEmpty()) {
            builder.workflowRunDecorator(new WorkflowRunDecorator() {
                @Override public <T> java.util.function.Supplier<T> decorate(java.util.function.Supplier<T> task) {
                    var result = task;
                    for (var d : taskDecoratorList) result = d.decorate(result);
                    return result;
                }
                @Override public WorkflowRunDecorator snapshot() {
                    var snapshots = taskDecoratorList.stream()
                            .map(WorkflowRunDecorator::snapshot).toList();
                    return new WorkflowRunDecorator() {
                        @Override public <T> java.util.function.Supplier<T> decorate(java.util.function.Supplier<T> task) {
                            var result = task;
                            for (var s : snapshots) result = s.decorate(result);
                            return result;
                        }
                    };
                }
            });
        }

        // Every CDI-registered AgenticanEventListener gets subscribed to the bus.
        // No composite/fan-out wrapper needed — the bus dispatches to each listener
        // synchronously in registration order.
        stepListeners.forEach(builder::addListener);

        llmClients.handlesStream().forEach(handle -> {

            var name = beanName(handle.getBean());

            if (name != null) {
                var client = handle.get();

                if (!llmDecoratorList.isEmpty()) {
                    var matchingConfig = findLlmConfigByName(engineConfig, name);
                    if (matchingConfig != null) {
                        for (var d : llmDecoratorList) client = d.decorate(matchingConfig, client);
                    }
                }

                builder.llm(name, client);
            }
        });

        toolkits.handlesStream().forEach(handle -> {

            var slug = beanName(handle.getBean());

            if (slug != null) builder.toolkit(slug, handle.get());
        });

        if (taskExecutors.isResolvable())
            builder.taskExecutor(taskExecutors.get());

        return builder.build();
    }

    public void disposeAgentican(@Disposes Agentican agentican) {

        agentican.close();
    }

    @Produces
    @jakarta.inject.Singleton
    public AgenticanRecovery agenticanRecovery(Agentican runtime) {

        return runtime.recovery();
    }

    public void disposeAgenticanRecovery(@Disposes AgenticanRecovery recovery) {

        recovery.close();
    }

    private static String beanName(Bean<?> bean) {

        var name = bean.getName();

        return name != null && !name.isBlank() ? name : null;
    }

    private static LlmConfig findLlmConfigByName(
            EngineConfig engine, String name) {

        return engine.llm().stream()
                .filter(c -> c.name().equals(name))
                .findFirst()
                .orElse(null);
    }
}
