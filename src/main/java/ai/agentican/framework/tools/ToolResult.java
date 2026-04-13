package ai.agentican.framework.tools;

public record ToolResult(
        String toolCallId,
        String toolName,
        String content,
        Throwable cause) {

    public ToolResult {

        if (toolCallId == null || toolCallId.isBlank())
            throw new IllegalArgumentException("Tool call ID is required");

        if (toolName == null || toolName.isBlank())
            throw new IllegalArgumentException("Tool name is required");

        if (content == null)
            throw new IllegalArgumentException("Content is required");
    }

    public ToolResult(String toolCallId, String toolName, String content) {

        this(toolCallId, toolName, content, null);
    }

    public boolean isError() {

        return cause != null;
    }
}
