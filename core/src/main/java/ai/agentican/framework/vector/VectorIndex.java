package ai.agentican.framework.vector;

import ai.agentican.framework.vector.VectorHit;

import java.util.List;
import java.util.Map;

public interface VectorIndex {

    String name();

    String description();

    IndexResult index(String text, Map<String, String> metadata);

    List<VectorHit> retrieve(String query, int k);
}
