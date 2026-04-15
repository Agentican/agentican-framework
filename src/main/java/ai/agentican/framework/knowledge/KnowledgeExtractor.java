package ai.agentican.framework.knowledge;

import java.util.List;

public interface KnowledgeExtractor {

    KnowledgeExtraction extract(String input, String output, List<KnowledgeEntrySummary> existingEntries);
}
