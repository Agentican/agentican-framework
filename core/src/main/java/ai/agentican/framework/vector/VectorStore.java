package ai.agentican.framework.vector;

import java.util.Collection;
import java.util.List;

public interface VectorStore {

    void upsert(List<VectorRecord> records);

    List<VectorHit> search(float[] queryVector, int k);

    void delete(Collection<String> ids);

    int dimensions();
}
