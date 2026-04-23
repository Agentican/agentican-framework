package ai.agentican.quarkus;

import ai.agentican.framework.invoker.AgenticanTask;
import ai.agentican.framework.orchestration.execution.TaskHandle;
import ai.agentican.framework.orchestration.execution.TaskResult;

import io.smallrye.mutiny.Uni;

public interface ReactiveAgenticanTask<P, R> {

    Uni<TaskHandle> run(P params);

    default Uni<TaskHandle> run() {

        return run(null);
    }

    Uni<R> runAndAwait(P params);

    default Uni<R> runAndAwait() {

        return runAndAwait(null);
    }

    Uni<TaskResult> awaitTaskResult(P params);

    default Uni<TaskResult> awaitTaskResult() {

        return awaitTaskResult(null);
    }

    static <P, R> ReactiveAgenticanTask<P, R> of(AgenticanTask<P, R> delegate) {

        return new ReactiveAgenticanAdapter<>(delegate);
    }
}
