package ai.agentican.quarkus;

import ai.agentican.framework.orchestration.execution.WorkflowRunResult;
import ai.agentican.framework.orchestration.execution.WorkflowRunStatus;
import ai.agentican.framework.orchestration.execution.WorkflowRun;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReactiveAgenticanAdapterTest {

    @Test
    void startDelegatesToSyncStart() {

        var called = new AtomicReference<String>();
        var run = stringRun("tid", "value");

        Function<String, WorkflowRun<String>> sync = p -> { called.set(p); return run; };

        var got = new ReactiveWorkflowAdapter<>(sync).start("params").await().atMost(Duration.ofSeconds(1));

        assertEquals(run, got);
        assertEquals("params", called.get());
    }

    @Test
    void runAndAwaitDelegatesViaStart() {

        Function<String, WorkflowRun<String>> sync = p -> stringRun("tid", "value:" + p);

        var got = new ReactiveWorkflowAdapter<>(sync).runAndAwait("x").await().atMost(Duration.ofSeconds(1));

        assertEquals("value:x", got);
    }

    @Test
    void awaitTaskResultReturnsUntyped() {

        var taskResult = okResult();
        Function<String, WorkflowRun<String>> sync = p -> rawRun("tid", taskResult, "ignored");

        var got = new ReactiveWorkflowAdapter<>(sync).awaitTaskResult("p").await().atMost(Duration.ofSeconds(1));

        assertEquals(taskResult, got);
    }

    @Test
    void uniIsLazyDoesNotSubmitUntilSubscribed() {

        var submitted = new AtomicBoolean();

        Function<String, WorkflowRun<String>> sync = p -> {
            submitted.set(true);
            return stringRun("tid", "v");
        };

        var uni = new ReactiveWorkflowAdapter<>(sync).runAndAwait("x");

        assertTrue(!submitted.get(), "Uni should not trigger submission before subscription");

        uni.await().atMost(Duration.ofSeconds(1));

        assertTrue(submitted.get(), "Subscription should have triggered submission");
    }

    private static WorkflowRunResult okResult() {

        return new WorkflowRunResult("definition", WorkflowRunStatus.COMPLETED, List.of());
    }

    private static WorkflowRun<String> stringRun(String id, String value) {

        return rawRun(id, okResult(), value);
    }

    private static <R> WorkflowRun<R> rawRun(String id, WorkflowRunResult taskResult, R typedValue) {

        return new WorkflowRun<>(
                id,
                CompletableFuture.completedFuture(taskResult),
                new AtomicBoolean(),
                r -> typedValue);
    }
}
