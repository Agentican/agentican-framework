package ai.agentican.framework.store;

import ai.agentican.framework.knowledge.KnowledgeEntry;
import ai.agentican.framework.knowledge.KnowledgeStatus;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class KnowledgeStoreMemory implements KnowledgeStore {

    private final ConcurrentHashMap<String, KnowledgeEntry> entries = new ConcurrentHashMap<>();

    @Override
    public void save(KnowledgeEntry entry) {

        entries.put(entry.id(), entry);
    }

    @Override
    public KnowledgeEntry get(String entryId) {

        return entries.get(entryId);
    }

    @Override
    public List<KnowledgeEntry> all() {

        return List.copyOf(entries.values());
    }

    @Override
    public List<KnowledgeEntry> indexed() {

        return entries.values().stream()
                .filter(e -> e.status() == KnowledgeStatus.INDEXED)
                .toList();
    }

    @Override
    public void delete(String entryId) {

        entries.remove(entryId);
    }
}
