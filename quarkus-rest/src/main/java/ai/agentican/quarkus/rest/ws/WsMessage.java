package ai.agentican.quarkus.rest.ws;

import ai.agentican.framework.orchestration.model.Plan;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record WsMessage(
        String action,
        String description,
        Plan task,
        Map<String, String> inputs,
        String taskId,
        String checkpointId,
        Boolean approved,
        String feedback) {}
