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

    public static SkillConfigBuilder builder() {

        return new SkillConfigBuilder();
    }

    public static class SkillConfigBuilder {

        private String id;
        private String name;
        private String instructions;
        private String externalId;

        public SkillConfigBuilder id(String id) { this.id = id; return this; }
        public SkillConfigBuilder name(String name) { this.name = name; return this; }
        public SkillConfigBuilder instructions(String instructions) { this.instructions = instructions; return this; }
        public SkillConfigBuilder externalId(String externalId) { this.externalId = externalId; return this; }

        public SkillConfig build() {

            return new SkillConfig(id, name, instructions, externalId);
        }
    }
}
