package ai.agentican.framework.event;

public record PlanCompleted(String taskId, String planId) implements AgenticanEvent { }
