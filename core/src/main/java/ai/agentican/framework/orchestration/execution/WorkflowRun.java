package ai.agentican.framework.orchestration.execution;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

public final class WorkflowRun<R> {

    private final String id;
    private final CompletableFuture<WorkflowRunResult> rawFuture;
    private final AtomicBoolean cancelled;
    private final Function<WorkflowRunResult, R> extractor;

    public WorkflowRun(String id,
                       CompletableFuture<WorkflowRunResult> rawFuture,
                       AtomicBoolean cancelled,
                       Function<WorkflowRunResult, R> extractor) {

        this.id = id;
        this.rawFuture = rawFuture;
        this.cancelled = cancelled;
        this.extractor = extractor;
    }

    public String id() { return id; }

    public boolean isDone() { return rawFuture.isDone(); }

    public boolean isCancelled() { return cancelled.get(); }

    public void cancel() { cancelled.set(true); }

    public R await() { return extractor.apply(rawFuture.join()); }

    public CompletableFuture<R> future() { return rawFuture.thenApply(extractor); }

    public WorkflowRunResult untypedResult() { return rawFuture.join(); }

    public CompletableFuture<WorkflowRunResult> untypedFuture() { return rawFuture; }
}
