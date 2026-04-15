package ai.agentican.framework.config;

import ai.agentican.framework.util.Ids;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SkillConfig(
        String id,
        String name,
        String instructions,
        String externalId) {

    public SkillConfig {

        if (name == null || name.isBlank())
            throw new IllegalArgumentException("Skill name is required");

        if (instructions == null || instructions.isBlank())
            throw new IllegalArgumentException("Skill instructions are required for skill '" + name + "'");

        if (id == null || id.isBlank())
            id = Ids.generate();

        if (externalId != null && externalId.isBlank())
            externalId = null;
    }

    public SkillConfig(String id, String name, String instructions) {

        this(id, name, instructions, null);
    }

    public static SkillConfig of(String name, String instructions) {

        return new SkillConfig(null, name, instructions, null);
    }

    public static SkillConfig of(String id, String name, String instructions) {

        return new SkillConfig(id, name, instructions, null);
    }

    public static SkillConfig forCatalog(String externalId, String name, String instructions) {

        return new SkillConfig(null, name, instructions, externalId);
    }
}
