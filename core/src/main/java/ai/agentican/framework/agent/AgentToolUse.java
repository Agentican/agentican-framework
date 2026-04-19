package ai.agentican.framework.agent;

public record AgentToolUse(
        String toolName,
        String input,
        String output) {

}
