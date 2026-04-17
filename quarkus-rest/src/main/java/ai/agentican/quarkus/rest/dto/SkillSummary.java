package ai.agentican.quarkus.rest.dto;

import ai.agentican.framework.config.SkillConfig;

public record SkillSummary(String id, String name, String instructions) {

    public static SkillSummary of(SkillConfig skill) {

        return new SkillSummary(skill.id(), skill.name(), skill.instructions());
    }
}
