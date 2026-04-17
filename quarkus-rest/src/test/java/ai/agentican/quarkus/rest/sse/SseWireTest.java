package ai.agentican.quarkus.rest.sse;

import ai.agentican.framework.state.TaskLog;
import ai.agentican.framework.orchestration.model.Plan;
import ai.agentican.framework.orchestration.execution.TaskStatus;
import ai.agentican.quarkus.event.StepCompletedEvent;
import ai.agentican.quarkus.event.TaskCompletedEvent;
import ai.agentican.quarkus.event.TaskStartedEvent;
import ai.agentican.quarkus.rest.TaskEventBus;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class SseWireTest {

    @Inject
    TaskEventBus bus;

    @Test
    void sseStreamDeliversNamedEventsOverHttp() throws Exception {

        var taskId = "sse-wire-" + System.nanoTime();
        var receivedLines = new CopyOnWriteArrayList<String>();
        var latch = new CountDownLatch(1);

        var port = io.restassured.RestAssured.port;
        var url = "http://localhost:" + port + "/agentican/tasks/" + taskId + "/stream";

        var client = HttpClient.newHttpClient();
        var request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Accept", "text/event-stream")
                .GET()
                .build();

        var future = client.sendAsync(request, HttpResponse.BodyHandlers.ofLines())
                .thenAccept(response -> response.body().forEach(line -> {
                    receivedLines.add(line);
                    if (line.startsWith("event:") && line.contains("task_completed"))
                        latch.countDown();
                }));

        Thread.sleep(300);

        var log = newTaskLog(taskId);

        bus.onTaskStarted(new TaskStartedEvent(taskId, "demo", log));
        bus.onStepCompleted(new StepCompletedEvent(null, taskId, "s", TaskStatus.COMPLETED));
        bus.onTaskCompleted(new TaskCompletedEvent(taskId, "demo", TaskStatus.COMPLETED, log));

        assertTrue(latch.await(10, TimeUnit.SECONDS), "Should receive task_completed event");
        future.cancel(true);

        assertTrue(receivedLines.stream().anyMatch(l -> l.contains("event:task_started") || l.contains("event: task_started")),
                "SSE stream should contain task_started event name. Lines: " + receivedLines);
        assertTrue(receivedLines.stream().anyMatch(l -> l.contains("event:step_completed") || l.contains("event: step_completed")),
                "SSE stream should contain step_completed event name");
        assertTrue(receivedLines.stream().anyMatch(l -> l.contains("event:task_completed") || l.contains("event: task_completed")),
                "SSE stream should contain task_completed event name");

        assertTrue(receivedLines.stream().anyMatch(l -> l.startsWith("id:")),
                "SSE stream should contain id: lines for replay support");
    }

    private static TaskLog newTaskLog(String taskId) {

        var task = Plan.builder("demo").description("d").step("s", "a", "i").build();
        return new TaskLog(taskId, "demo", task, Map.of());
    }
}
