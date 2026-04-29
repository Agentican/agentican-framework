package ai.agentican.quarkus.rest.dto;

public record UpdateAgentRequest(

        String name,

        String role,

        String llm) {
}
