package ai.agentican.temporal.activity;

import ai.agentican.framework.knowledge.KnowledgeEntry;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

import java.util.List;

@ActivityInterface
public interface KnowledgeStoreActivity {

    @ActivityMethod
    void save(KnowledgeEntry entry);

    @ActivityMethod
    KnowledgeEntry get(String entryId);

    @ActivityMethod
    List<KnowledgeEntry> all();

    @ActivityMethod
    List<KnowledgeEntry> indexed();

    @ActivityMethod
    void delete(String entryId);
}
