package ai.agentican.framework.event;

public record StepStarted(String taskId, String stepId, String stepName) implements AgenticanEvent { }
