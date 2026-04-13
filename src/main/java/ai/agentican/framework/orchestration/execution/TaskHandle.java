package ai.agentican.framework.orchestration.execution;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

public class TaskHandle {

    private final String taskId;
    private final CompletableFuture<TaskResult> future;
    private final AtomicBoolean cancelled;

    public TaskHandle(String taskId, CompletableFuture<TaskResult> future, AtomicBoolean cancelled) {

        this.taskId = taskId;
        this.future = future;
        this.cancelled = cancelled;
    }

    public String taskId() {

        return taskId;
    }

    public TaskResult result() {

        return future.join();
    }

    public CompletableFuture<TaskResult> resultAsync() {

        return future;
    }

    public void cancel() {

        cancelled.set(true);
    }

    public boolean isDone() {

        return future.isDone();
    }

    public boolean isCancelled() {

        return cancelled.get();
    }
}
