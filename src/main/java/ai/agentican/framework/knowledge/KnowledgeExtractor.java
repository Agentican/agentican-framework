package ai.agentican.framework.knowledge;

import java.util.List;

public interface KnowledgeExtractor {

    List<KnowledgeFact> extractFacts(String text);
}
