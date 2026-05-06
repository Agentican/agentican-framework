package ai.agentican.quarkus.rest.dto;

import ai.agentican.framework.orchestration.model.WorkflowDefinition;

import java.util.List;

public record CatalogSnapshot(

        List<AgentExport> agents,

        List<SkillExport> skills,

        List<WorkflowDefinition> workflows) {

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
