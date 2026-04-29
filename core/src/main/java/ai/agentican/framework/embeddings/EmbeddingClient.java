package ai.agentican.framework.embeddings;

import java.util.List;

public interface EmbeddingClient {

    List<float[]> embed(List<String> texts);

    default float[] embed(String text) {

        return embed(List.of(text)).getFirst();
    }

    int dimensions();

    String modelId();
}
