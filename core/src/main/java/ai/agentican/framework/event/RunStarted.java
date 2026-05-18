package ai.agentican.framework.event;

public record RunStarted(String taskId, String stepId, String runId, String agentName) implements AgenticanEvent { }
