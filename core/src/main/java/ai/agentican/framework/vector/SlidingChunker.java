package ai.agentican.framework.vector;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class SlidingChunker implements Chunker {

    public static final int DEFAULT_CHUNK_SIZE = 800;
    public static final int DEFAULT_OVERLAP    = 100;

    private final int chunkSize;
    private final int overlap;

    public SlidingChunker() {

        this(DEFAULT_CHUNK_SIZE, DEFAULT_OVERLAP);
    }

    public SlidingChunker(int chunkSize, int overlap) {

        if (chunkSize <= 0)
            throw new IllegalArgumentException("chunkSize must be > 0");

        if (overlap < 0)
            throw new IllegalArgumentException("overlap must be >= 0");

        if (overlap >= chunkSize)
            throw new IllegalArgumentException("overlap must be < chunkSize");

        this.chunkSize = chunkSize;
        this.overlap   = overlap;
    }

    @Override
    public List<Chunk> chunk(String text) {

        if (text == null) return List.of();

        var trimmed = text.strip();
        if (trimmed.isEmpty()) return List.of();

        if (trimmed.length() <= chunkSize)
            return List.of(new Chunk(trimmed, Map.of()));

        var chunks = new ArrayList<Chunk>();
        var step   = chunkSize - overlap;
        for (var start = 0; start < trimmed.length(); start += step) {
            var end = Math.min(start + chunkSize, trimmed.length());
            chunks.add(new Chunk(trimmed.substring(start, end), Map.of()));
            if (end == trimmed.length()) break;
        }
        return chunks;
    }

    public int chunkSize() { return chunkSize; }

    public int overlap()   { return overlap; }
}
