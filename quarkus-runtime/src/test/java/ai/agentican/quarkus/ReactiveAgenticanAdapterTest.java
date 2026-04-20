package ai.agentican.quarkus;

import ai.agentican.framework.invoker.Agentican;
import ai.agentican.framework.orchestration.execution.TaskHandle;
import ai.agentican.framework.orchestration.execution.TaskResult;
import ai.agentican.framework.orchestration.execution.TaskStatus;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReactiveAgenticanAdapterTest {

    @Test
    void runDelegatesToSyncRun() {

        var called = new AtomicReference<String>();
        var handle = new TaskHandle("tid", CompletableFuture.completedFuture(okResult()),
                                    new java.util.concurrent.atomic.AtomicBoolean());

        Agentican<String, String> sync = new FakeAgentican<>(
                p -> { called.set(p); return handle; }, null, null);

        var got = ReactiveAgentican.of(sync).run("params").await().atMost(Duration.ofSeconds(1));

        assertEquals(handle, got);
        assertEquals("params", called.get());
    }

    @Test
    void runAndAwaitDelegatesToRunAsync() {

        Agentican<String, String> sync = new FakeAgentican<>(
                null, null, p -> CompletableFuture.completedFuture("value:" + p));

        var got = ReactiveAgentican.of(sync).runAndAwait("x").await().atMost(Duration.ofSeconds(1));

        assertEquals("value:x", got);
    }

    @Test
    void awaitTaskResultReturnsSyncResult() {

        var result = okResult();
        var handle = new TaskHandle("tid", CompletableFuture.completedFuture(result),
                                    new java.util.concurrent.atomic.AtomicBoolean());

        Agentican<String, String> sync = new FakeAgentican<>(p -> handle, null, null);

        var got = ReactiveAgentican.of(sync).awaitTaskResult("p").await().atMost(Duration.ofSeconds(1));

        assertEquals(result, got);
    }

    @Test
    void uniIsLazyDoesNotSubmitUntilSubscribed() {

        var submitted = new AtomicBoolean();

        Agentican<String, String> sync = new FakeAgentican<>(
                null, null,
                p -> { submitted.set(true); return CompletableFuture.completedFuture(null); });

        var uni = ReactiveAgentican.of(sync).runAndAwait("x");

        assertTrue(!submitted.get(), "Uni should not trigger submission before subscription");

        uni.await().atMost(Duration.ofSeconds(1));

        assertTrue(submitted.get(), "Subscription should have triggered submission");
    }

    private static TaskResult okResult() {

        return new TaskResult("plan", TaskStatus.COMPLETED, List.of());
    }

    private record FakeAgentican<P, R>(
            java.util.function.Function<P, TaskHandle> runFn,
            java.util.function.Function<P, R> runAndAwaitFn,
            java.util.function.Function<P, CompletableFuture<R>> runAsyncFn) implements Agentican<P, R> {

        @Override public TaskHandle run(P params) {
            assertNotNull(runFn, "run() not expected");
            return runFn.apply(params);
        }

        @Override public R runAndAwait(P params) {
            assertNotNull(runAndAwaitFn, "runAndAwait() not expected");
            return runAndAwaitFn.apply(params);
        }

        @Override public CompletableFuture<R> runAsync(P params) {
            assertNotNull(runAsyncFn, "runAsync() not expected");
            return runAsyncFn.apply(params);
        }
    }
}
