package ai.agentican.quarkus.rest.dto;

public record CreateAgentRequest(

        String externalId,

        String name,

        String role,

        String llm) {
}
