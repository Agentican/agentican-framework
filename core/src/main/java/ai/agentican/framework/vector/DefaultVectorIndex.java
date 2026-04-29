package ai.agentican.framework.vector;

import ai.agentican.framework.embeddings.EmbeddingClient;
import ai.agentican.framework.vector.VectorHit;
import ai.agentican.framework.vector.VectorRecord;
import ai.agentican.framework.vector.VectorStore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

public final class DefaultVectorIndex implements VectorIndex {

    private final String           name;
    private final String           description;
    private final EmbeddingClient  embeddings;
    private final VectorStore      store;
    private final Chunker          chunker;
    private final Supplier<String> idGenerator;

    public DefaultVectorIndex(
            String          name,
            String          description,
            EmbeddingClient embeddings,
            VectorStore     store,
            Chunker         chunker) {

        this(name, description, embeddings, store, chunker,
                () -> UUID.randomUUID().toString());
    }

    public DefaultVectorIndex(
            String           name,
            String           description,
            EmbeddingClient  embeddings,
            VectorStore      store,
            Chunker          chunker,
            Supplier<String> idGenerator) {

        if (name == null || name.isBlank())
            throw new IllegalArgumentException("VectorIndex name is required");

        if (embeddings == null)
            throw new IllegalArgumentException("EmbeddingClient is required");

        if (store == null)
            throw new IllegalArgumentException("VectorStore is required");

        if (chunker == null)
            throw new IllegalArgumentException("Chunker is required");

        if (idGenerator == null)
            throw new IllegalArgumentException("idGenerator is required");

        if (embeddings.dimensions() != store.dimensions())
            throw new IllegalArgumentException(
                    "EmbeddingClient dimensions (" + embeddings.dimensions()
                  + ") must match VectorStore dimensions (" + store.dimensions() + ")");

        this.name        = name;
        this.description = description == null ? "" : description;
        this.embeddings  = embeddings;
        this.store       = store;
        this.chunker     = chunker;
        this.idGenerator = idGenerator;
    }

    @Override public String name()        { return name; }

    @Override public String description() { return description; }

    @Override
    public IndexResult index(String text, Map<String, String> metadata) {

        var chunks = chunker.chunk(text);
        if (chunks.isEmpty()) return new IndexResult(0, List.of());

        var contents = chunks.stream().map(Chunk::content).toList();
        var vectors  = embeddings.embed(contents);

        if (vectors.size() != chunks.size())
            throw new IllegalStateException(
                    "EmbeddingClient returned " + vectors.size()
                  + " vectors for " + chunks.size() + " chunks");

        var records = new ArrayList<VectorRecord>(chunks.size());
        var ids     = new ArrayList<String>(chunks.size());
        for (var i = 0; i < chunks.size(); i++) {
            var combined = new HashMap<String, String>(metadata == null ? Map.of() : metadata);
            combined.putAll(chunks.get(i).metadata());
            var id = idGenerator.get();
            ids.add(id);
            records.add(new VectorRecord(id, vectors.get(i), chunks.get(i).content(), combined));
        }

        store.upsert(records);
        return new IndexResult(chunks.size(), List.copyOf(ids));
    }

    @Override
    public List<VectorHit> retrieve(String query, int k) {

        if (query == null) query = "";
        if (k <= 0)        k     = 5;

        var vector = embeddings.embed(query);
        return store.search(vector, k);
    }
}
