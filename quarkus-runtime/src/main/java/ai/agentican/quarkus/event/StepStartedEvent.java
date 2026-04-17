package ai.agentican.quarkus.event;

public record StepStartedEvent(String stepId, String taskId, String stepName) {}
