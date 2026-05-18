package ai.agentican.framework.event;

public record StepResumed(String taskId, String stepId) implements AgenticanEvent { }
