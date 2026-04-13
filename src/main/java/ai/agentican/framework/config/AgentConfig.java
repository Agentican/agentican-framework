package ai.agentican.framework.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.ArrayList;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AgentConfig(
        String name,
        String role,
        String llm,
        List<SkillConfig> skills) {

    public AgentConfig {

        if (name == null || name.isBlank())
            throw new IllegalArgumentException("Agent name is required");

        if (role == null || role.isBlank())
            throw new IllegalArgumentException("Agent role is required");

        if (llm == null || llm.isBlank())
            llm = LlmConfig.DEFAULT;

        if (skills == null)
            skills = List.of();
    }

    public static AgentConfig of(String name, String role, String llm, List<SkillConfig> skills) {

        return new AgentConfig(name, role, llm, skills);
    }

    public static AgentConfigBuilder builder() {

        return new AgentConfigBuilder();
    }

    public static class AgentConfigBuilder {

        private String name;
        private String role;
        private String llm;

        private final List<SkillConfig> skills = new ArrayList<>();

        public AgentConfigBuilder skill(String name, String instructions) {

            var skillConfig = SkillConfig.of(name, instructions);

            return skill(skillConfig);
        }

        public AgentConfigBuilder name(String name) { this.name = name; return this; }
        public AgentConfigBuilder role(String role) { this.role = role; return this; }
        public AgentConfigBuilder llm(String llm) { this.llm = llm; return this; }
        public AgentConfigBuilder skill(SkillConfig skill) { this.skills.add(skill); return this; }
        public AgentConfigBuilder skills(List<SkillConfig> skills) { this.skills.addAll(skills); return this; }

        public AgentConfig build() {

            return AgentConfig.of(name, role, llm, skills);
        }
    }
}
