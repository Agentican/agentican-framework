package ai.agentican.framework.vector;

import java.util.Map;

public record VectorHit(
        String id,
        float score,
        String content,
        Map<String, String> metadata) {

    public VectorHit {

        if (id == null || id.isBlank())
            throw new IllegalArgumentException("VectorHit id is required");

        if (content == null) content = "";
        if (metadata == null) metadata = Map.of();
    }
}
