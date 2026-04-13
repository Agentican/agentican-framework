package ai.agentican.framework.knowledge;

import java.util.List;

public interface KnowledgeStore {

    void save(KnowledgeEntry entry);

    KnowledgeEntry get(String entryId);

    List<KnowledgeEntry> all();

    List<KnowledgeEntry> indexed();

    void delete(String entryId);
}
