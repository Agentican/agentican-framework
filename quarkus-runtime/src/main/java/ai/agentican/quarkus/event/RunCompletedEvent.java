package ai.agentican.quarkus.event;

public record RunCompletedEvent(String runId, String stepId, String agentName, int runIndex,
                                String taskId) {}
