package ai.agentican.quarkus.rest.sse;

public record SequencedEvent(long id, Object payload) {}
