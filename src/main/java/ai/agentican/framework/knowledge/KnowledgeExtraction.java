package ai.agentican.framework.knowledge;

import java.util.List;

public record KnowledgeExtraction(List<ExtractedEntry> entries) {

    public KnowledgeExtraction {

        if (entries == null) entries = List.of();
    }

    public static KnowledgeExtraction empty() {

        return new KnowledgeExtraction(List.of());
    }
}
