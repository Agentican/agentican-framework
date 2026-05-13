package ai.agentican.temporal.activity;

import ai.agentican.framework.knowledge.KnowledgeEntry;
import ai.agentican.framework.store.KnowledgeStore;

import java.util.List;

public class KnowledgeStoreActivityImpl implements KnowledgeStoreActivity {

    private final KnowledgeStore delegate;

    public KnowledgeStoreActivityImpl(KnowledgeStore delegate) {

        if (delegate == null) throw new IllegalArgumentException("delegate KnowledgeStore is required");

        this.delegate = delegate;
    }

    @Override public void save(KnowledgeEntry entry) { delegate.save(entry); }
    @Override public KnowledgeEntry get(String entryId) { return delegate.get(entryId); }
    @Override public List<KnowledgeEntry> all() { return delegate.all(); }
    @Override public List<KnowledgeEntry> indexed() { return delegate.indexed(); }
    @Override public void delete(String entryId) { delegate.delete(entryId); }
}
