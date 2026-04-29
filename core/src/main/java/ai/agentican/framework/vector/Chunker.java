package ai.agentican.framework.vector;

import java.util.List;

@FunctionalInterface
public interface Chunker {

    List<Chunk> chunk(String text);
}
