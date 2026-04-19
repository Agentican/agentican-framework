package ai.agentican.framework.config;

import ai.agentican.framework.util.Ids;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AgentConfig(
        String id,
        String name,
        String role,
        String llm,
        String externalId) {

    public AgentConfig {

        if (name == null || name.isBlank())
            throw new IllegalArgumentException("Agent name is required");

        if (role == null || role.isBlank())
            throw new IllegalArgumentException("Agent role is required");

        if (id == null || id.isBlank())
            id = Ids.generate();

        if (llm == null || llm.isBlank())
            llm = LlmConfig.DEFAULT;

        if (externalId != null && externalId.isBlank())
            externalId = null;
    }

    public static AgentConfigBuilder builder() {

        return new AgentConfigBuilder();
    }

    public static class AgentConfigBuilder {

        private String id;
        private String name;
        private String role;
        private String llm;
        private String externalId;

        public AgentConfigBuilder id(String id) { this.id = id; return this; }
        public AgentConfigBuilder name(String name) { this.name = name; return this; }
        public AgentConfigBuilder role(String role) { this.role = role; return this; }
        public AgentConfigBuilder llm(String llm) { this.llm = llm; return this; }
        public AgentConfigBuilder externalId(String externalId) { this.externalId = externalId; return this; }

        public AgentConfig build() {

            return new AgentConfig(id, name, role, llm, externalId);
        }
    }
}
