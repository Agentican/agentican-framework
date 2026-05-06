package ai.agentican.quarkus.rest.ws;

import ai.agentican.quarkus.rest.dto.WorkflowDefinitionInput;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record WsMessage(
        String action,
        String description,
        WorkflowDefinitionInput task,
        Map<String, String> inputs,
        String taskId,
        String checkpointId,
        Boolean approved,
        String feedback) {}
