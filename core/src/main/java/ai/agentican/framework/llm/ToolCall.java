package ai.agentican.framework.llm;

import java.util.Map;

public record ToolCall(
        String id,
        String toolName,
        Map<String, Object> args) {

    public ToolCall {

        if (id == null || id.isBlank())
            throw new IllegalArgumentException("Tool call ID is required");

        if (toolName == null || toolName.isBlank())
            throw new IllegalArgumentException("Tool name is required");

        if (args == null)
            args = Map.of();
    }
}
