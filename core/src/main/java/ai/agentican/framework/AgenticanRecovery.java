package ai.agentican.framework;

import ai.agentican.framework.hitl.HitlManager;
import ai.agentican.framework.knowledge.KnowledgeIngestor;
import ai.agentican.framework.orchestration.execution.TaskRunner;
import ai.agentican.framework.orchestration.execution.TaskStatus;
import ai.agentican.framework.orchestration.execution.resume.ReapReason;
import ai.agentican.framework.orchestration.execution.resume.ResumeClassifier;
import ai.agentican.framework.state.StepLog;
import ai.agentican.framework.state.TaskLog;
import ai.agentican.framework.store.TaskStateStore;
import ai.agentican.framework.util.Mdc;
import ai.agentican.framework.orchestration.execution.TaskDecorator;
import ai.agentican.framework.orchestration.execution.TaskListener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

public class AgenticanRecovery implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(AgenticanRecovery.class);

    private final TaskStateStore taskStateStore;
    private final TaskListener taskListener;
    private final TaskRunner taskRunner;
    private final ExecutorService taskExecutor;
    private final TaskDecorator taskDecorator;
    private final HitlManager hitlManager;
    private final KnowledgeIngestor knowledgeIngestor;

    private final CopyOnWriteArrayList<CompletableFuture<?>> reingestFutures = new CopyOnWriteArrayList<>();

    public AgenticanRecovery(AgenticanRuntime agentican) {

        var internals = agentican.internals();

        this.taskStateStore = internals.taskStateStore();
        this.taskListener = internals.taskListener();
        this.taskRunner = internals.taskRunner();
        this.taskExecutor = internals.taskExecutor();
        this.taskDecorator = internals.taskDecorator();
        this.hitlManager = internals.hitlManager();
        this.knowledgeIngestor = internals.knowledgeIngestor();
    }

    public int reapOrphans() {

        return reapOrphans(ReapReason.SERVER_RESTARTED);
    }

    public int reapOrphans(ReapReason reason) {

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

    public int resumeInterrupted() {

        return resumeInterrupted(10);
    }

    public int resumeInterrupted(int maxConcurrent) {

        var tasks = taskStateStore.listInProgress();
        int resumed = 0;
        int reaped = 0;
        var semaphore = new Semaphore(maxConcurrent > 0 ? maxConcurrent : 1, true);

        for (var task : tasks) {

            if (task.status() != null) continue;
            if (task.parentTaskId() != null) continue;

            var plan = task.plan();

            var resumePlan = ResumeClassifier.classify(task, plan);

            if (resumePlan.reapOnly()) {

                LOG.warn("Task {} ({}) cannot be resumed: {} — reaping",
                        task.taskName(), task.taskId(),
                        resumePlan.reapReason() != null ? resumePlan.reapReason().name() : "UNKNOWN");

                reapSingleTask(task, resumePlan.reapReason() != null
                        ? resumePlan.reapReason()
                        : ReapReason.UNKNOWN);
                reaped++;
                continue;
            }

            rehydratePendingCheckpoints(task);
            reingestCompletedSteps(task);

            LOG.info("Task {} ({}) resume classification: completedSteps={}, inFlightStep={}, turnState={}, pendingTools={} — "
                            + "submitting to executor",
                    task.taskName(), task.taskId(),
                    resumePlan.completedSteps().size(),
                    resumePlan.inFlightStep().map(StepLog::stepName).orElse("<none>"),
                    resumePlan.turnState(),
                    resumePlan.toolsToExecute().size());

            var taskId = task.taskId();
            var params = task.params();

            var cancelled = new AtomicBoolean(false);

            var submitted = wrapTaskRunner(Mdc.propagate(() -> {
                try {
                    semaphore.acquire();
                    taskListener.onTaskResumed(taskId);
                    return taskRunner.resume(plan, taskId, params, cancelled);
                }
                catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    LOG.warn("Resume of task {} interrupted while waiting for concurrency slot", taskId);
                    taskListener.onTaskCompleted(taskId, TaskStatus.CANCELLED);
                    throw new CompletionException(e);
                }
                catch (Exception e) {
                    LOG.error("Resume of task {} failed: {}", taskId, e.getMessage(), e);
                    taskListener.onTaskCompleted(taskId, TaskStatus.FAILED);
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

    @Override
    public void close() {

        var pending = reingestFutures.toArray(new CompletableFuture[0]);

        if (pending.length == 0) return;

        try {
            CompletableFuture.allOf(pending).get(10, TimeUnit.SECONDS);
        }
        catch (TimeoutException ex) {
            LOG.warn("Knowledge re-ingestion did not finish within 10s on close; {} job(s) abandoned", pending.length);
        }
        catch (Exception ex) {
            LOG.warn("Knowledge re-ingestion wait interrupted on close: {}", ex.getMessage());
            Thread.currentThread().interrupt();
        }
    }

    private <T> Supplier<T> wrapTaskRunner(Supplier<T> supplier) {

        return taskDecorator != null ? taskDecorator.decorate(supplier) : supplier;
    }

    private int reapDanglingSubTasks(List<TaskLog> inProgress) {

        int reaped = 0;
        for (var t : inProgress) {
            if (t.parentTaskId() == null) continue;

            var parent = taskStateStore.load(t.parentTaskId());
            if (parent == null || parent.status() == null) continue;

            LOG.warn("Reaping dangling sub-task {} (parent {} already terminal: {})",
                    t.taskId(), t.parentTaskId(), parent.status());
            reapSingleTask(t, ReapReason.DANGLING_PARENT_TERMINAL);
            reaped++;
        }

        return reaped;
    }

    private void reingestCompletedSteps(TaskLog task) {

        if (knowledgeIngestor == null) return;

        var taskId = task.taskId();
        var stepIds = task.steps().values().stream()
                .filter(s -> s.status() == TaskStatus.COMPLETED)
                .filter(s -> s.output() != null && !s.output().isBlank())
                .map(StepLog::id)
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

    private void rehydratePendingCheckpoints(TaskLog task) {

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

    private void reapSingleTask(TaskLog task, ReapReason reason) {

        reapOrphanedSubTasks(task.taskId(), reason);

        for (var step : task.steps().values()) {
            if (step.status() == null)
                taskStateStore.stepCompleted(task.taskId(), step.id(), TaskStatus.FAILED,
                        "Step abandoned: " + reason.name());
        }
        taskStateStore.taskCompleted(task.taskId(), TaskStatus.FAILED);
        taskListener.onTaskReaped(task.taskId(), reason);
    }

    private void reapOrphanedSubTasks(String parentTaskId, ReapReason reason) {

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
            taskListener.onTaskReaped(candidate.taskId(), ReapReason.PARENT_REAPED);
        }
    }
}
