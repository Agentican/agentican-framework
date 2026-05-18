package ai.agentican.framework;

import ai.agentican.framework.event.AgenticanEventBus;
import ai.agentican.framework.event.PlanCompleted;
import ai.agentican.framework.event.PlanStarted;
import ai.agentican.framework.event.TaskCompleted;
import ai.agentican.framework.hitl.HitlManager;
import ai.agentican.framework.knowledge.KnowledgeIngestor;
import ai.agentican.framework.orchestration.execution.OutputBinding;
import ai.agentican.framework.orchestration.execution.WorkflowRun;
import ai.agentican.framework.orchestration.execution.WorkflowRunDecorator;
import ai.agentican.framework.orchestration.execution.WorkflowRunResult;
import ai.agentican.framework.orchestration.execution.WorkflowRunStatus;
import ai.agentican.framework.orchestration.execution.WorkflowRunner;
import ai.agentican.framework.orchestration.model.WorkflowDefinition;
import ai.agentican.framework.store.WorkflowRunStore;
import ai.agentican.framework.util.Ids;
import ai.agentican.framework.util.Mdc;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

public final class WorkflowEngine implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(WorkflowEngine.class);

    final AgenticanRegistry registry;
    final WorkflowRunStore workflowRunStore;
    final AgenticanEventBus eventBus;
    final WorkflowRunner taskRunner;
    final ExecutorService taskExecutor;
    final WorkflowRunDecorator workflowRunDecorator;
    final HitlManager hitlManager;
    final KnowledgeIngestor knowledgeIngestor;
    final boolean ownsExecutor;

    WorkflowEngine(AgenticanRegistry registry, WorkflowRunStore workflowRunStore, AgenticanEventBus eventBus,
                   WorkflowRunner taskRunner, ExecutorService taskExecutor, WorkflowRunDecorator workflowRunDecorator,
                   HitlManager hitlManager, KnowledgeIngestor knowledgeIngestor, boolean ownsExecutor) {

        this.registry = registry;
        this.workflowRunStore = workflowRunStore;
        this.eventBus = eventBus;
        this.taskRunner = taskRunner;
        this.taskExecutor = taskExecutor;
        this.workflowRunDecorator = workflowRunDecorator;
        this.hitlManager = hitlManager;
        this.knowledgeIngestor = knowledgeIngestor;
        this.ownsExecutor = ownsExecutor;
    }

    public AgenticanEventBus eventBus() { return eventBus; }

    <R> WorkflowRun<R> dispatch(WorkflowDefinition plan, Map<String, String> inputs, OutputBinding outputBinding,
                                Function<WorkflowRunResult, R> extractor) {

        return submit((taskId, cancelled) -> {

            registry.workflows().registerIfAbsent(plan);

            eventBus.publish(new PlanStarted(taskId));
            eventBus.publish(new PlanCompleted(taskId, plan.id()));

            return taskRunner.run(plan, taskId, inputs, cancelled, outputBinding);

        }, extractor);
    }

    <R> WorkflowRun<R> submit(BiFunction<String, AtomicBoolean, WorkflowRunResult> taskFn,
                              Function<WorkflowRunResult, R> extractor) {

        var taskId = Ids.generate();
        var cancelled = new AtomicBoolean(false);

        var supplier = wrapTaskRunner(Mdc.propagate(() -> {

            try {

                return taskFn.apply(taskId, cancelled);
            }
            catch (Exception e) {

                LOG.error("Task {} failed: {}", taskId, e.getMessage(), e);

                eventBus.publish(new TaskCompleted(taskId, WorkflowRunStatus.FAILED));

                throw e;
            }
        }));

        var future = CompletableFuture.supplyAsync(supplier, taskExecutor);

        return new WorkflowRun<>(taskId, future, cancelled, extractor);
    }

    private <T> Supplier<T> wrapTaskRunner(Supplier<T> supplier) {

        return workflowRunDecorator != null ? workflowRunDecorator.decorate(supplier) : supplier;
    }

    public AgenticanRegistry registry() {

        return registry;
    }

    @Override
    public void close() {

        if (ownsExecutor) taskExecutor.shutdownNow();
    }
}
