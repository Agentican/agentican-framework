package ai.agentican.framework.knowledge;

import java.util.List;

public interface FactExtractor {

    List<Fact> extractFacts(String text);
}
