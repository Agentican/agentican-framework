package ai.agentican.temporal.dto;

import java.util.Map;

public record ToolCallRequest(String toolName, Map<String, Object> arguments) {

    public ToolCallRequest {

        if (toolName == null || toolName.isBlank())
            throw new IllegalArgumentException("toolName is required");

        if (arguments == null) arguments = Map.of();
    }
}
