package ai.agentican.quarkus.event;

public record RunStartedEvent(String runId, String stepId, String agentName, int runIndex,
                              String taskId) {}
