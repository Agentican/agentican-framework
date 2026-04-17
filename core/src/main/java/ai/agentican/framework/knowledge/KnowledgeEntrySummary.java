package ai.agentican.framework.knowledge;

public record KnowledgeEntrySummary(String id, String name, String description, int factCount) {

    public static KnowledgeEntrySummary of(KnowledgeEntry entry) {

        return new KnowledgeEntrySummary(entry.id(), entry.name(),
                entry.description() != null ? entry.description() : "",
                entry.facts().size());
    }
}
