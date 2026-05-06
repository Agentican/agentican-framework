package ai.agentican.quarkus.rest.dto;

public record CreateSkillRequest(

        String name,

        String instructions) {
}
