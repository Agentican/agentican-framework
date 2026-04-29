package ai.agentican.framework.embeddings;

import java.util.List;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;

public final class RecordingEmbeddingClient implements EmbeddingClient {

    public final List<List<String>> calls = new CopyOnWriteArrayList<>();

    private final int    dimensions;
    private final String modelId;

    public RecordingEmbeddingClient(int dimensions) {

        this(dimensions, "recording");
    }

    public RecordingEmbeddingClient(int dimensions, String modelId) {

        this.dimensions = dimensions;
        this.modelId    = modelId;
    }

    @Override
    public List<float[]> embed(List<String> texts) {

        calls.add(List.copyOf(texts));
        return texts.stream().map(this::vectorFor).toList();
    }

    @Override public int    dimensions() { return dimensions; }

    @Override public String modelId()    { return modelId; }

    private float[] vectorFor(String text) {

        var seed = text == null ? 0 : text.hashCode();
        var rnd  = new Random(seed);
        var v    = new float[dimensions];
        for (var i = 0; i < dimensions; i++) v[i] = rnd.nextFloat() * 2f - 1f;
        return v;
    }
}
