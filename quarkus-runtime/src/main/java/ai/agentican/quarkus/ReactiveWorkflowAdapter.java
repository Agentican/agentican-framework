package ai.agentican.quarkus;

import ai.agentican.framework.orchestration.execution.WorkflowRunResult;
import ai.agentican.framework.orchestration.execution.WorkflowRun;

import io.smallrye.mutiny.Uni;

import java.util.function.Function;

/**
 * Holds a {@code Function<P, WorkflowRun<R>>} (typically {@code Workflow::start}
 * via {@link ReactiveWorkflow#of}) rather than a Workflow directly. This keeps
 * the adapter narrow — it only needs the start dispatch — and lets tests inject
 * a function without implementing Workflow.
 */
record ReactiveWorkflowAdapter<P, R>(Function<P, WorkflowRun<R>> startFn) implements ReactiveWorkflow<P, R> {

    ReactiveWorkflowAdapter {

        if (startFn == null)
            throw new IllegalArgumentException("startFn is required");
    }

    @Override
    public Uni<WorkflowRun<R>> start(P params) {

        return Uni.createFrom().item(() -> startFn.apply(params));
    }

    @Override
    public Uni<R> runAndAwait(P params) {

        return Uni.createFrom().completionStage(() -> startFn.apply(params).future());
    }

    @Override
    public Uni<WorkflowRunResult> awaitTaskResult(P params) {

        return Uni.createFrom().completionStage(() -> startFn.apply(params).untypedFuture());
    }
}
