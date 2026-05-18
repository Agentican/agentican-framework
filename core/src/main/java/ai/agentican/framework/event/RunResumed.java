package ai.agentican.framework.event;

public record RunResumed(String taskId, String runId) implements AgenticanEvent { }
