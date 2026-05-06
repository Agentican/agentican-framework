package ai.agentican.quarkus.rest.dto;

import java.util.List;

public record CatalogSnapshot(

        List<AgentExport> agents,

        List<SkillExport> skills,

        List<WorkflowDefinitionInput> workflows) {

    public record AgentExport(

            String id,
            String name,
            String role,
            String llm) {}

    public record SkillExport(

            String id,
            String name,
            String instructions) {}
}
