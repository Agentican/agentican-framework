package ai.agentican.quarkus;

import ai.agentican.framework.Workflow;
import ai.agentican.framework.orchestration.execution.WorkflowRunResult;
import ai.agentican.framework.orchestration.execution.WorkflowRun;

import io.smallrye.mutiny.Uni;

public interface ReactiveWorkflow<P, R> {

    Uni<WorkflowRun<R>> start(P params);

    default Uni<WorkflowRun<R>> start() {

        return start(null);
    }

    Uni<R> runAndAwait(P params);

    default Uni<R> runAndAwait() {

        return runAndAwait(null);
    }

    Uni<WorkflowRunResult> awaitTaskResult(P params);

    default Uni<WorkflowRunResult> awaitTaskResult() {

        return awaitTaskResult(null);
    }

    static <P, R> ReactiveWorkflow<P, R> of(Workflow<P, R> delegate) {

        return new ReactiveWorkflowAdapter<>(delegate::start);
    }
}
