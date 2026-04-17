package ai.agentican.quarkus.event;

public record IterationStartedEvent(
        String iterationId,
        String parentStepId,
        String parentTaskId,
        String iterationName,
        int index) {}
