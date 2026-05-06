package ai.agentican.quarkus;

import ai.agentican.framework.Agentican;
import ai.agentican.framework.AgenticanRecovery;
import ai.agentican.framework.registry.AgentRegistry;
import ai.agentican.framework.hitl.HitlManager;
import ai.agentican.framework.store.KnowledgeStore;
import ai.agentican.framework.registry.WorkflowRegistry;
import ai.agentican.framework.registry.SkillRegistry;
import ai.agentican.framework.orchestration.execution.WorkflowRunListener;
import ai.agentican.framework.orchestration.execution.WorkflowRunDecorator;
import ai.agentican.framework.llm.LlmClient;
import ai.agentican.framework.llm.LlmClientDecorator;
import ai.agentican.framework.store.WorkflowRunStore;
import ai.agentican.framework.orchestration.execution.WorkflowRunStatus;
import ai.agentican.framework.tools.Toolkit;
import ai.agentican.framework.hitl.HitlCheckpoint;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Disposes;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Produces;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.inject.Inject;

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
    Instance<WorkflowRunListener> stepListeners;

    @Inject
    Instance<java.util.concurrent.ExecutorService> taskExecutors;

    @Produces
    @jakarta.inject.Singleton
    public ai.agentican.framework.config.EngineConfig engineConfig() {

        var fromProps = EngineConfigConverter.fromProperties(config);
        var fromYaml = loadEngineYaml(config.engineConfig());

        return fromYaml != null ? EngineConfigConverter.merge(fromYaml, fromProps) : fromProps;
    }

    @Produces
    @jakarta.inject.Singleton
    public ai.agentican.framework.config.CatalogConfig catalogConfig() {

        return switch (config.catalogSource()) {
            case yaml -> loadCatalogYaml(config.catalogConfig());
            case database -> new ai.agentican.framework.config.CatalogConfig(
                    java.util.List.of(), java.util.List.of(), java.util.List.of());
        };
    }

    private static ai.agentican.framework.config.EngineConfig loadEngineYaml(String resourcePath) {

        try (var in = openClasspath(resourcePath)) {

            if (in == null) return null; // engine YAML is optional
            return ai.agentican.framework.config.EngineConfig.load(in);
        }
        catch (java.io.IOException e) {
            throw new IllegalStateException("Failed to load engine config from " + resourcePath, e);
        }
    }

    private static ai.agentican.framework.config.CatalogConfig loadCatalogYaml(String resourcePath) {

        try (var in = openClasspath(resourcePath)) {

            if (in == null)
                throw new IllegalStateException(
                        "agentican.catalog-config not found on classpath: " + resourcePath
                                + ". Place a CatalogConfig YAML at src/main/resources/" + resourcePath
                                + ", or set agentican.catalog-source=database.");

            return ai.agentican.framework.config.CatalogConfig.load(in);
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
    public Agentican agentican(ai.agentican.framework.config.EngineConfig engineConfig,
                               ai.agentican.framework.config.CatalogConfig catalogConfig) {

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

        var listenerList = stepListeners.stream().toList();
        if (!listenerList.isEmpty()) {
            builder.workflowRunListener(new WorkflowRunListener() {
                @Override public void onPlanStarted(String taskId) {
                    listenerList.forEach(l -> l.onPlanStarted(taskId));
                }
                @Override public void onPlanCompleted(String taskId, String planId) {
                    listenerList.forEach(l -> l.onPlanCompleted(taskId, planId));
                }
                @Override public void onTaskStarted(String taskId) {
                    listenerList.forEach(l -> l.onTaskStarted(taskId));
                }
                @Override public void onTaskCompleted(String taskId, WorkflowRunStatus status) {
                    listenerList.forEach(l -> l.onTaskCompleted(taskId, status));
                }
                @Override public void onStepStarted(String taskId, String stepId) {
                    listenerList.forEach(l -> l.onStepStarted(taskId, stepId));
                }
                @Override public void onStepCompleted(String taskId, String stepId) {
                    listenerList.forEach(l -> l.onStepCompleted(taskId, stepId));
                }
                @Override public void onRunStarted(String taskId, String runId) {
                    listenerList.forEach(l -> l.onRunStarted(taskId, runId));
                }
                @Override public void onRunCompleted(String taskId, String runId,
                                                      ai.agentican.framework.agent.AgentStatus status) {
                    listenerList.forEach(l -> l.onRunCompleted(taskId, runId, status));
                }
                @Override public void onTurnStarted(String taskId, String turnId) {
                    listenerList.forEach(l -> l.onTurnStarted(taskId, turnId));
                }
                @Override public void onTurnCompleted(String taskId, String turnId) {
                    listenerList.forEach(l -> l.onTurnCompleted(taskId, turnId));
                }
                @Override public void onMessageSent(String taskId, String turnId) {
                    listenerList.forEach(l -> l.onMessageSent(taskId, turnId));
                }
                @Override public void onResponseReceived(String taskId, String turnId,
                                                          ai.agentican.framework.llm.StopReason stopReason) {
                    listenerList.forEach(l -> l.onResponseReceived(taskId, turnId, stopReason));
                }
                @Override public void onToolCallStarted(String taskId, String toolCallId) {
                    listenerList.forEach(l -> l.onToolCallStarted(taskId, toolCallId));
                }
                @Override public void onToolCallCompleted(String taskId, String toolCallId) {
                    listenerList.forEach(l -> l.onToolCallCompleted(taskId, toolCallId));
                }
                @Override public void onHitlNotified(String taskId, String hitlId,
                                                      ai.agentican.framework.hitl.HitlCheckpoint.Type type) {
                    listenerList.forEach(l -> l.onHitlNotified(taskId, hitlId, type));
                }
                @Override public void onHitlResponded(String taskId, String hitlId, boolean approved) {
                    listenerList.forEach(l -> l.onHitlResponded(taskId, hitlId, approved));
                }
                @Override public void onToken(String taskId, String turnId, String token) {
                    listenerList.forEach(l -> l.onToken(taskId, turnId, token));
                }
            });
        }

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

    private static ai.agentican.framework.config.LlmConfig findLlmConfigByName(
            ai.agentican.framework.config.EngineConfig engine, String name) {

        return engine.llm().stream()
                .filter(c -> c.name().equals(name))
                .findFirst()
                .orElse(null);
    }
}
