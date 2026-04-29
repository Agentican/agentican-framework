package ai.agentican.framework.vector;

import java.util.Map;

public record Chunk(String content, Map<String, String> metadata) {

    public Chunk {

        if (content == null || content.isEmpty())
            throw new IllegalArgumentException("Chunk content is required");

        if (metadata == null) metadata = Map.of();
    }
}
