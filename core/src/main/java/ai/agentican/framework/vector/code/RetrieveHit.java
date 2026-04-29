package ai.agentican.framework.vector.code;

import java.util.Map;

public record RetrieveHit(
        String              id,
        double              score,
        String              content,
        Map<String, String> metadata) {

    public RetrieveHit {

        if (id == null || id.isBlank())
            throw new IllegalArgumentException("RetrieveHit id is required");

        if (content == null) content = "";
        if (metadata == null) metadata = Map.of();
    }
}
