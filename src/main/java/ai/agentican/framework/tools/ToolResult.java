package ai.agentican.framework.tools;

public record ToolResult(
        String toolCallId,
        String toolName,
        String content,
        Throwable cause,
        State state) {

    public enum State { STARTED, COMPLETED, FAILED }

    public ToolResult {

        if (toolCallId == null || toolCallId.isBlank())
            throw new IllegalArgumentException("Tool call ID is required");

        if (toolName == null || toolName.isBlank())
            throw new IllegalArgumentException("Tool name is required");

        if (content == null)
            throw new IllegalArgumentException("Content is required");

        if (state == null)
            state = cause != null ? State.FAILED : State.COMPLETED;
    }

    public ToolResult(String toolCallId, String toolName, String content) {

        this(toolCallId, toolName, content, null, State.COMPLETED);
    }

    public ToolResult(String toolCallId, String toolName, String content, Throwable cause) {

        this(toolCallId, toolName, content, cause, cause != null ? State.FAILED : State.COMPLETED);
    }

    public static ToolResult started(String toolCallId, String toolName) {

        return new ToolResult(toolCallId, toolName, "", null, State.STARTED);
    }

    public boolean isError() {

        return cause != null;
    }

    public boolean isPending() {

        return state == State.STARTED;
    }
}
