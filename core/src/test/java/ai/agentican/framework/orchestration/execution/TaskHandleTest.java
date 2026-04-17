package ai.agentican.framework.orchestration.execution;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class TaskHandleTest {

    @Test
    void resultBlocksUntilComplete() {

        var future = new CompletableFuture<TaskResult>();
        var handle = new TaskHandle("test-id", future, new AtomicBoolean(false));

        var expected = new TaskResult("test", TaskStatus.COMPLETED, List.of());

        Thread.startVirtualThread(() -> {
            try { Thread.sleep(100); } catch (InterruptedException _) {}
            future.complete(expected);
        });

        var result = handle.result();

        assertSame(expected, result);
    }

    @Test
    void cancelSetsFlag() {

        var future = new CompletableFuture<TaskResult>();
        var cancelled = new AtomicBoolean(false);
        var handle = new TaskHandle("test-id", future, cancelled);

        assertFalse(handle.isCancelled());

        handle.cancel();

        assertTrue(handle.isCancelled());
    }

    @Test
    void isDoneReflectsFuture() {

        var future = new CompletableFuture<TaskResult>();
        var handle = new TaskHandle("test-id", future, new AtomicBoolean(false));

        assertFalse(handle.isDone());

        future.complete(new TaskResult("test", TaskStatus.COMPLETED, List.of()));

        assertTrue(handle.isDone());
    }

    @Test
    void taskIdIsExposed() {

        var future = new CompletableFuture<TaskResult>();
        var handle = new TaskHandle("abc12345", future, new AtomicBoolean(false));

        assertEquals("abc12345", handle.taskId());
    }

    @Test
    void resultThrowsOnFutureException() {

        var future = new CompletableFuture<TaskResult>();
        future.completeExceptionally(new RuntimeException("task failed"));
        var handle = new TaskHandle("test-id", future, new AtomicBoolean(false));

        assertThrows(CompletionException.class, () -> handle.result());
    }
}
