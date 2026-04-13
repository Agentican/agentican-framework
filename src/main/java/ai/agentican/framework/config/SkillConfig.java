package ai.agentican.framework.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SkillConfig(
        String name,
        String instructions) {

    public SkillConfig {

        if (name == null || name.isBlank())
            throw new IllegalArgumentException("Skill name is required");

        if (instructions == null || instructions.isBlank())
            throw new IllegalArgumentException("Skill instructions are required for skill '" + name + "'");
    }

    public static SkillConfig of(String name, String instructions) {

        return new SkillConfig(name, instructions);
    }
}
