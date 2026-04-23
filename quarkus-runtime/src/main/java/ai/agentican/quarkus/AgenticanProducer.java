package ai.agentican.quarkus;

import ai.agentican.framework.Agentican;
import ai.agentican.framework.AgenticanRecovery;
import ai.agentican.framework.registry.AgentRegistry;
import ai.agentican.framework.hitl.HitlManager;
import ai.agentican.framework.store.KnowledgeStore;
import ai.agentican.framework.registry.PlanRegistry;
import ai.agentican.framework.registry.SkillRegistry;
import ai.agentican.framework.orchestration.execution.TaskListener;
import ai.agentican.framework.orchestration.execution.TaskDecorator;
import ai.agentican.framework.llm.LlmClient;
import ai.agentican.framework.llm.LlmClientDecorator;
import ai.agentican.framework.store.TaskStateStore;
import ai.agentican.framework.orchestration.execution.TaskStatus;
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
    TaskStateStore taskStateStore;

    @Inject
    AgentRegistry agentRegistry;

    @Inject
    SkillRegistry skillRegistry;

    @Inject
    PlanRegistry planRegistry;

    @Inject
    Instance<LlmClient> llmClients;

    @Inject
    Instance<Toolkit> toolkits;

    @Inject
    Instance<LlmClientDecorator> llmDecorators;

    @Inject
    Instance<TaskDecorator> taskDecorators;

    @Inject
    Instance<TaskListener> stepListeners;

    @Inject
    Instance<java.util.concurrent.ExecutorService> taskExecutors;

    @Produces
    @ApplicationScoped
    @io.quarkus.runtime.Startup
    public Agentican agentican() {

        var runtimeConfig = RuntimeConfigConverter.toRuntimeConfig(config);

        var builder = Agentican.builder(runtimeConfig)
                .hitlManager(hitlManager)
                .knowledgeStore(knowledgeStore)
                .taskStateStore(taskStateStore)
                .agentRegistry(agentRegistry)
                .skillRegistry(skillRegistry)
                .planRegistry(planRegistry);

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
            builder.taskDecorator(new TaskDecorator() {
                @Override public <T> java.util.function.Supplier<T> decorate(java.util.function.Supplier<T> task) {
                    var result = task;
                    for (var d : taskDecoratorList) result = d.decorate(result);
                    return result;
                }
                @Override public TaskDecorator snapshot() {
                    var snapshots = taskDecoratorList.stream()
                            .map(TaskDecorator::snapshot).toList();
                    return new TaskDecorator() {
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
            builder.stepListener(new TaskListener() {
                @Override public void onPlanStarted(String taskId) {
                    listenerList.forEach(l -> l.onPlanStarted(taskId));
                }
                @Override public void onPlanCompleted(String taskId, String planId) {
                    listenerList.forEach(l -> l.onPlanCompleted(taskId, planId));
                }
                @Override public void onTaskStarted(String taskId) {
                    listenerList.forEach(l -> l.onTaskStarted(taskId));
                }
                @Override public void onTaskCompleted(String taskId, TaskStatus status) {
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
                    var matchingConfig = findLlmConfigByName(runtimeConfig, name);
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
    @ApplicationScoped
    public AgenticanRecovery agenticanRecovery(Agentican runtime) {

        return new AgenticanRecovery(runtime);
    }

    public void disposeAgenticanRecovery(@Disposes AgenticanRecovery recovery) {

        recovery.close();
    }

    private static String beanName(Bean<?> bean) {

        var name = bean.getName();

        return name != null && !name.isBlank() ? name : null;
    }

    private static ai.agentican.framework.config.LlmConfig findLlmConfigByName(
            ai.agentican.framework.config.RuntimeConfig config, String name) {

        return config.llm().stream()
                .filter(c -> c.name().equals(name))
                .findFirst()
                .orElse(null);
    }
}
