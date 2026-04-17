package ai.agentican.quarkus.rest.ws;

import ai.agentican.quarkus.test.MockLlmClient;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class AgenticanWebSocketTest {

    @Inject
    MockLlmClient mockLlm;

    @BeforeEach
    void reset() {

        mockLlm.reset();
    }

    @Test
    void connectAndReceiveWelcome() throws Exception {

        var port = io.restassured.RestAssured.port;
        var messages = new CopyOnWriteArrayList<String>();
        var latch = new CountDownLatch(1);

        var ws = HttpClient.newHttpClient().newWebSocketBuilder()
                .buildAsync(URI.create("ws://localhost:" + port + "/agentican/ws"),
                        new WebSocket.Listener() {
                            @Override
                            public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                                messages.add(data.toString());
                                latch.countDown();
                                webSocket.request(1);
                                return null;
                            }
                        })
                .get(5, TimeUnit.SECONDS);

        assertTrue(latch.await(5, TimeUnit.SECONDS), "Should receive welcome message");
        assertTrue(messages.getFirst().contains("connected"),
                "Welcome should contain 'connected'. Got: " + messages.getFirst());

        ws.sendClose(WebSocket.NORMAL_CLOSURE, "done").get(5, TimeUnit.SECONDS);
    }

    @Test
    void submitTaskViaWebSocket() throws Exception {

        mockLlm.queueEndTurn("WS result");

        var port = io.restassured.RestAssured.port;
        var messages = new CopyOnWriteArrayList<String>();
        var latch = new CountDownLatch(2);

        var ws = HttpClient.newHttpClient().newWebSocketBuilder()
                .buildAsync(URI.create("ws://localhost:" + port + "/agentican/ws"),
                        new WebSocket.Listener() {
                            @Override
                            public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                                messages.add(data.toString());
                                latch.countDown();
                                webSocket.request(1);
                                return null;
                            }
                        })
                .get(5, TimeUnit.SECONDS);

        ws.sendText("{\"action\":\"submit_task\",\"task\":{\"name\":\"ws-test\"," +
                "\"description\":\"d\",\"steps\":[{\"type\":\"agent\",\"name\":\"s\"," +
                "\"agentId\":\"researcher\",\"instructions\":\"do it\"}]}}", true)
                .get(5, TimeUnit.SECONDS);

        assertTrue(latch.await(5, TimeUnit.SECONDS),
                "Should receive welcome + task_submitted");
        assertTrue(messages.stream().anyMatch(m -> m.contains("task_submitted")),
                "Should receive task_submitted. Got: " + messages);

        ws.sendClose(WebSocket.NORMAL_CLOSURE, "done").get(5, TimeUnit.SECONDS);
    }
}
