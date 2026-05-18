package ai.agentican.framework.event;

public record TokenStreamed(String taskId, String turnId, String token) implements AgenticanEvent { }
