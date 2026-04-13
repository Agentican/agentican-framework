package ai.agentican.framework.agent;

public enum AgentStatus {

    COMPLETED,
    CANCELLED,
    TIMED_OUT,
    MAX_TURNS,
    SUSPENDED;

    public String defaultText() {

        return switch (this) {

            case COMPLETED -> "Task completed";
            case CANCELLED -> "Task cancelled";
            case TIMED_OUT -> "Task timed out";
            case MAX_TURNS -> "Task exceeded max turns";
            case SUSPENDED -> "Task suspended";
        };
    }
}
