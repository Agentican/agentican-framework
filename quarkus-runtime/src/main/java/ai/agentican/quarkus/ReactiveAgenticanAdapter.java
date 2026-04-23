package ai.agentican.quarkus;

import ai.agentican.framework.invoker.AgenticanTask;
import ai.agentican.framework.orchestration.execution.TaskHandle;
import ai.agentican.framework.orchestration.execution.TaskResult;

import io.smallrye.mutiny.Uni;

record ReactiveAgenticanAdapter<P, R>(AgenticanTask<P, R> delegate) implements ReactiveAgenticanTask<P, R> {

    ReactiveAgenticanAdapter {

        if (delegate == null)
            throw new IllegalArgumentException("delegate AgenticanTask is required");
    }

    @Override
    public Uni<TaskHandle> run(P params) {

        return Uni.createFrom().item(() -> delegate.run(params));
    }

    @Override
    public Uni<R> runAndAwait(P params) {

        return Uni.createFrom().completionStage(() -> delegate.runAsync(params));
    }

    @Override
    public Uni<TaskResult> awaitTaskResult(P params) {

        return Uni.createFrom().completionStage(() -> delegate.run(params).resultAsync());
    }
}
