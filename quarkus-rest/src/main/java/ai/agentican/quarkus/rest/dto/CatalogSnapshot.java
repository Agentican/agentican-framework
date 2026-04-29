package ai.agentican.quarkus.rest.dto;

import ai.agentican.framework.orchestration.model.Plan;

import java.util.List;

public record CatalogSnapshot(

        List<AgentExport> agents,

        List<SkillExport> skills,

        List<Plan> plans) {

    public record AgentExport(

            String externalId,
            String name,
            String role,
            String llm) {}

    public record SkillExport(

            String externalId,
            String name,
            String instructions) {}
}
