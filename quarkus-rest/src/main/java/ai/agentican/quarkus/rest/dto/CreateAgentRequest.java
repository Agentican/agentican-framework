package ai.agentican.quarkus.rest.dto;

public record CreateAgentRequest(

        String name,

        String role,

        String llm) {
}
