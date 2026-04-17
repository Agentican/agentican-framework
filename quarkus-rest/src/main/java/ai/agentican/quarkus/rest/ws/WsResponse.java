package ai.agentican.quarkus.rest.ws;

import java.util.Map;

public record WsResponse(String type, Map<String, Object> data) {

    public static WsResponse taskSubmitted(String taskId) {

        return new WsResponse("task_submitted", Map.of("taskId", taskId));
    }

    public static WsResponse event(String eventType, Map<String, Object> payload) {

        return new WsResponse(eventType, payload);
    }

    public static WsResponse error(String message) {

        return new WsResponse("error", Map.of("message", message));
    }

    public static WsResponse ok(String message) {

        return new WsResponse("ok", Map.of("message", message));
    }
}
