package ai.agentican.framework.event;

public record TurnAbandoned(String taskId, String turnId) implements AgenticanEvent { }
