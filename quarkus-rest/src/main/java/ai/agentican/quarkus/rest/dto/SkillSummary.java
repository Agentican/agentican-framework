package ai.agentican.quarkus.rest.dto;

import ai.agentican.framework.config.SkillConfig;

public record SkillSummary(

        String id,
        String name,
        String instructions,
        boolean declaredInConfig) {

    public static SkillSummary of(SkillConfig skill, boolean declaredInConfig) {

        return new SkillSummary(
                skill.id(),
                skill.name(),
                skill.instructions(),
                declaredInConfig);
    }
}
