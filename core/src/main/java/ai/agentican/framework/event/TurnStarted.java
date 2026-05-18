package ai.agentican.framework.event;

public record TurnStarted(String taskId, String runId, String turnId, int index) implements AgenticanEvent { }
