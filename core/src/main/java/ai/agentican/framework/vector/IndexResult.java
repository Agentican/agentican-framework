package ai.agentican.framework.vector;

import java.util.List;

public record IndexResult(int chunkCount, List<String> ids) {

    public IndexResult {

        if (chunkCount < 0)
            throw new IllegalArgumentException("chunkCount must be >= 0");

        if (ids == null) ids = List.of();

        if (chunkCount != ids.size())
            throw new IllegalArgumentException(
                    "chunkCount " + chunkCount + " must match ids.size() " + ids.size());
    }
}
