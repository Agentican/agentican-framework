package ai.agentican.framework;

import ai.agentican.framework.event.StepCompleted;
import ai.agentican.framework.event.TaskCompleted;
import ai.agentican.framework.event.TaskReaped;
import ai.agentican.framework.event.TaskResumed;
import ai.agentican.framework.knowledge.KnowledgeIngestor;
import ai.agentican.framework.orchestration.execution.WorkflowRunStatus;
import ai.agentican.framework.orchestration.execution.resume.ReapReason;
import ai.agentican.framework.orchestration.execution.resume.ResumeClassifier;
import ai.agentican.framework.state.RuntimeOwner;
import ai.agentican.framework.state.StepLog;
import ai.agentican.framework.state.WorkflowRunLog;
import ai.agentican.framework.util.Mdc;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

public class AgenticanRecovery implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(AgenticanRecovery.class);

    private final WorkflowEngine engine;

    private final CopyOnWriteArrayList<CompletableFuture<?>> reingestFutures = new CopyOnWriteArrayList<>();

    public AgenticanRecovery(WorkflowEngine engine) {

        this.engine = engine;
    }

    public int reapOrphans() {

        return reapOrphans(ReapReason.SERVER_RESTARTED);
    }

    public int reapOrphans(ReapReason reason) {

        var workflowRunStore = engine.workflowRunStore;

        var tasks = workflowRunStore.listInProgress();

        int reaped = 0;

        for (var task : tasks) {

            if (task.status() != null) continue;
            if (task.parentTaskId() != null) continue;
            if (task.runtime() == RuntimeOwner.TEMPORAL) {
                LOG.debug("Skipping reap of Temporal-owned task {} ({}, workflowId={}) — Temporal handles its own recovery",
                        task.taskName(), task.taskId(), task.temporalWorkflowId());
                continue;
            }

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

        var workflowRunStore = engine.workflowRunStore;
        var eventBus = engine.eventBus;
        var taskRunner = engine.taskRunner;
        var taskExecutor = engine.taskExecutor;

        var tasks = workflowRunStore.listInProgress();

        int resumed = 0;
        int reaped = 0;

        var semaphore = new Semaphore(maxConcurrent > 0 ? maxConcurrent : 1, true);

        for (var task : tasks) {

            if (task.status() != null) continue;
            if (task.parentTaskId() != null) continue;
            if (task.runtime() == RuntimeOwner.TEMPORAL) {
                LOG.debug("Skipping resume of Temporal-owned task {} ({}, workflowId={}) — Temporal handles its own recovery",
                        task.taskName(), task.taskId(), task.temporalWorkflowId());
                continue;
            }

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
                    eventBus.publish(new TaskResumed(taskId));
                    return taskRunner.resume(plan, taskId, params, cancelled);
                }
                catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    LOG.warn("Resume of task {} interrupted while waiting for concurrency slot", taskId);
                    eventBus.publish(new TaskCompleted(taskId, WorkflowRunStatus.CANCELLED));
                    throw new CompletionException(e);
                }
                catch (Exception e) {
                    LOG.error("Resume of task {} failed: {}", taskId, e.getMessage(), e);
                    eventBus.publish(new TaskCompleted(taskId, WorkflowRunStatus.FAILED));
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

        var workflowRunDecorator = engine.workflowRunDecorator;

        return workflowRunDecorator != null ? workflowRunDecorator.decorate(supplier) : supplier;
    }

    private int reapDanglingSubTasks(List<WorkflowRunLog> inProgress) {

        var workflowRunStore = engine.workflowRunStore;

        int reaped = 0;
        for (var t : inProgress) {
            if (t.parentTaskId() == null) continue;
            if (t.runtime() == RuntimeOwner.TEMPORAL) continue;  // Temporal owns its sub-task lifecycle

            var parent = workflowRunStore.load(t.parentTaskId());
            if (parent == null || parent.status() == null) continue;

            LOG.warn("Reaping dangling sub-task {} (parent {} already terminal: {})",
                    t.taskId(), t.parentTaskId(), parent.status());
            reapSingleTask(t, ReapReason.DANGLING_PARENT_TERMINAL);
            reaped++;
        }

        return reaped;
    }

    private void reingestCompletedSteps(WorkflowRunLog task) {

        var knowledgeIngestor = engine.knowledgeIngestor;
        var taskExecutor = engine.taskExecutor;

        if (knowledgeIngestor == null) return;

        var taskId = task.taskId();
        var completedSteps = task.steps().values().stream()
                .filter(s -> s.status() == WorkflowRunStatus.COMPLETED)
                .filter(s -> s.output() != null && !s.output().isBlank())
                .filter(s -> s.output().contains(KnowledgeIngestor.ACQUIRED_MARKER))
                .toList();

        if (completedSteps.isEmpty()) return;

        var future = CompletableFuture.runAsync(() -> {
            for (var step : completedSteps) {
                try {
                    // Recovery is a batch operation — we resolve the first turn's user task
                    // from the persisted log ourselves rather than going through the
                    // event-driven path (whose listener state is empty on startup).
                    var input = firstTurnUserTask(step);
                    var cleanedOutput = step.output().replace(KnowledgeIngestor.ACQUIRED_MARKER, "").stripTrailing();
                    knowledgeIngestor.ingestStep(step.stepName(), input, cleanedOutput);
                }
                catch (RuntimeException ex) {
                    LOG.warn("Knowledge re-ingestion for step {} of task {} failed: {}",
                            step.id(), taskId, ex.getMessage());
                }
            }
        }, taskExecutor);

        reingestFutures.add(future);
        future.whenComplete((v, ex) -> reingestFutures.remove(future));
    }

    private void rehydratePendingCheckpoints(WorkflowRunLog task) {

        var hitlManager = engine.hitlManager;

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

    private void reapSingleTask(WorkflowRunLog task, ReapReason reason) {

        var eventBus = engine.eventBus;

        reapOrphanedSubTasks(task.taskId(), reason);

        for (var step : task.steps().values()) {
            if (step.status() == null)
                eventBus.publish(new StepCompleted(task.taskId(), step.id(), step.stepName(),
                        WorkflowRunStatus.FAILED, "Step abandoned: " + reason.name()));
        }
        eventBus.publish(new TaskCompleted(task.taskId(), WorkflowRunStatus.FAILED));
        eventBus.publish(new TaskReaped(task.taskId(), reason));
    }

    private void reapOrphanedSubTasks(String parentTaskId, ReapReason reason) {

        var workflowRunStore = engine.workflowRunStore;
        var eventBus = engine.eventBus;

        var all = workflowRunStore.list();
        for (var candidate : all) {
            if (!parentTaskId.equals(candidate.parentTaskId())) continue;
            if (candidate.status() != null) continue;

            reapOrphanedSubTasks(candidate.taskId(), reason);

            for (var step : candidate.steps().values()) {
                if (step.status() == null)
                    eventBus.publish(new StepCompleted(candidate.taskId(), step.id(), step.stepName(),
                            WorkflowRunStatus.FAILED, "Step abandoned: " + reason.name()));
            }
            eventBus.publish(new TaskCompleted(candidate.taskId(), WorkflowRunStatus.FAILED));
            eventBus.publish(new TaskReaped(candidate.taskId(), ReapReason.PARENT_REAPED));
        }
    }

    private static String firstTurnUserTask(StepLog step) {

        if (step.runs().isEmpty()) return null;
        var firstRun = step.runs().getFirst();
        if (firstRun.turns().isEmpty()) return null;
        var firstTurn = firstRun.turns().getFirst();
        return firstTurn.request() != null ? firstTurn.request().userTask() : null;
    }
}
