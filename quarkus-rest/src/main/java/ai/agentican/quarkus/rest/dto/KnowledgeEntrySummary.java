package ai.agentican.quarkus.rest.dto;

import ai.agentican.framework.knowledge.KnowledgeEntry;

import java.time.Instant;

public record KnowledgeEntrySummary(
        String id,
        String name,
        String description,
        String status,
        int factCount,
        Instant created,
        Instant updated) {

    public static KnowledgeEntrySummary of(KnowledgeEntry entry) {

        return new KnowledgeEntrySummary(
                entry.id(),
                entry.name(),
                entry.description(),
                entry.status().name(),
                entry.facts().size(),
                entry.created(),
                entry.updated());
    }
}
