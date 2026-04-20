package ai.agentican.framework.knowledge;

import java.util.List;

public interface KnowledgeExtractor {

    List<ExtractedEntry> extract(String input, String output, List<KnowledgeEntry> existingEntries);
}
