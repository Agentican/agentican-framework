package ai.agentican.framework.store;

import ai.agentican.framework.knowledge.KnowledgeEntry;

import java.util.List;

public interface KnowledgeStore {

    void save(KnowledgeEntry entry);

    KnowledgeEntry get(String entryId);

    List<KnowledgeEntry> all();

    List<KnowledgeEntry> indexed();

    void delete(String entryId);
}
