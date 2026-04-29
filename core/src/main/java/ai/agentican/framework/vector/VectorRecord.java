package ai.agentican.framework.vector;

import java.util.Map;

public record VectorRecord(
        String id,
        float[] vector,
        String content,
        Map<String, String> metadata) {

    public VectorRecord {

        if (id == null || id.isBlank())
            throw new IllegalArgumentException("VectorRecord id is required");

        if (vector == null || vector.length == 0)
            throw new IllegalArgumentException("VectorRecord vector is required");

        if (content == null) content = "";
        if (metadata == null) metadata = Map.of();
    }
}
