package ai.agentican.quarkus.rest.dto;

import ai.agentican.framework.knowledge.KnowledgeFact;
import ai.agentican.framework.knowledge.KnowledgeEntry;

import java.time.Instant;
import java.util.List;

public record KnowledgeEntryView(
        String id,
        String name,
        String description,
        String status,
        List<KnowledgeFactView> facts,
        Instant created,
        Instant updated) {

    public static KnowledgeEntryView of(KnowledgeEntry entry) {

        var facts = entry.facts().stream()
                .map(KnowledgeFactView::of)
                .toList();

        return new KnowledgeEntryView(
                entry.id(),
                entry.name(),
                entry.description(),
                entry.status().name(),
                facts,
                entry.created(),
                entry.updated());
    }

    public record KnowledgeFactView(String id, String name, String content, List<String> tags,
                            Instant created, Instant updated) {

        public static KnowledgeFactView of(KnowledgeFact fact) {

            return new KnowledgeFactView(
                    fact.id(), fact.name(), fact.content(),
                    fact.tags(), fact.created(), fact.updated());
        }
    }
}
