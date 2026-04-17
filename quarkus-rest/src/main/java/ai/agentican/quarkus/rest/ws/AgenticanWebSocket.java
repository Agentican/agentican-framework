package ai.agentican.quarkus.rest.ws;

import ai.agentican.framework.hitl.HitlManager;
import ai.agentican.framework.hitl.HitlResponse;
import ai.agentican.quarkus.rest.TaskEventBus;
import ai.agentican.quarkus.rest.TaskService;
import ai.agentican.quarkus.rest.sse.SseEventTypes;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.websockets.next.OnClose;
import io.quarkus.websockets.next.OnOpen;
import io.quarkus.websockets.next.OnTextMessage;
import io.quarkus.websockets.next.WebSocket;
import io.quarkus.websockets.next.WebSocketConnection;
import jakarta.inject.Inject;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@WebSocket(path = "/agentican/ws")
public class AgenticanWebSocket {

    @Inject
    TaskService taskService;

    @Inject
    TaskEventBus eventBus;

    @Inject
    HitlManager hitlManager;

    @Inject
    ObjectMapper objectMapper;

    private final ConcurrentHashMap<String, io.smallrye.mutiny.subscription.Cancellable> subscriptions
            = new ConcurrentHashMap<>();

    @OnOpen
    public WsResponse onOpen(WebSocketConnection connection) {

        return WsResponse.ok("connected");
    }

    @OnTextMessage
    public WsResponse onMessage(WebSocketConnection connection, WsMessage message) {

        if (message == null || message.action() == null)
            return WsResponse.error("action is required");

        return switch (message.action()) {

            case "submit" -> handleSubmit(message);
            case "submit_task" -> handleSubmitTask(message);
            case "respond" -> handleRespond(message);
            case "cancel" -> handleCancel(message);
            case "subscribe" -> handleSubscribe(connection, message);

            default -> WsResponse.error("Unknown action: " + message.action());
        };
    }

    @OnClose
    public void onClose(WebSocketConnection connection) {

        subscriptions.values().forEach(io.smallrye.mutiny.subscription.Cancellable::cancel);
        subscriptions.clear();
    }

    private WsResponse handleSubmit(WsMessage message) {

        if (message.description() == null || message.description().isBlank())
            return WsResponse.error("description is required for submit");

        var handle = taskService.submit(message.description());

        return WsResponse.taskSubmitted(handle.taskId());
    }

    private WsResponse handleSubmitTask(WsMessage message) {

        if (message.task() == null)
            return WsResponse.error("task is required for submit_task");

        var inputs = message.inputs() != null ? message.inputs() : Map.<String, String>of();
        var handle = taskService.submit(message.task(), inputs);

        return WsResponse.taskSubmitted(handle.taskId());
    }

    private WsResponse handleRespond(WsMessage message) {

        if (message.checkpointId() == null)
            return WsResponse.error("checkpointId is required for respond");

        if (message.approved() == null)
            return WsResponse.error("approved is required for respond");

        if (!hitlManager.pendingCheckpoints().containsKey(message.checkpointId()))
            return WsResponse.error("No pending checkpoint: " + message.checkpointId());

        hitlManager.respond(message.checkpointId(),
                new HitlResponse(message.approved(), message.feedback()));
        eventBus.clearCheckpoint(message.checkpointId());

        return WsResponse.ok("checkpoint resolved");
    }

    private WsResponse handleCancel(WsMessage message) {

        if (message.taskId() == null)
            return WsResponse.error("taskId is required for cancel");

        var handle = taskService.handleFor(message.taskId());

        if (handle == null)
            return WsResponse.error("No active task: " + message.taskId());

        handle.cancel();

        return WsResponse.ok("task cancelled");
    }

    private WsResponse handleSubscribe(WebSocketConnection connection, WsMessage message) {

        if (message.taskId() == null)
            return WsResponse.error("taskId is required for subscribe");

        var taskId = message.taskId();

        var cancellable = eventBus.stream(taskId).subscribe().with(
                event -> {
                    try {
                        var typeName = SseEventTypes.nameFor(event.payload());
                        var json = objectMapper.writeValueAsString(
                                WsResponse.event(typeName, Map.of(
                                        "taskId", taskId,
                                        "eventId", event.id())));
                        connection.sendText(json).subscribe().with(v -> {}, e -> {});
                    } catch (Exception e) {

                    }
                },
                failure -> {},
                () -> {
                    try {
                        var json = objectMapper.writeValueAsString(
                                WsResponse.event("stream_completed",
                                        Map.of("taskId", taskId)));
                        connection.sendText(json).subscribe().with(v -> {}, e -> {});
                    } catch (Exception e) {

                    }
                });

        subscriptions.put(taskId, cancellable);

        return WsResponse.ok("subscribed to " + taskId);
    }
}
