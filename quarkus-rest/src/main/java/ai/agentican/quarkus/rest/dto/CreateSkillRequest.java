package ai.agentican.quarkus.rest.dto;

public record CreateSkillRequest(

        String externalId,

        String name,

        String instructions) {
}
