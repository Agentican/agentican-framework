package ai.agentican.framework.vector.provider;

import ai.agentican.framework.vector.VectorHit;
import ai.agentican.framework.vector.VectorRecord;
import ai.agentican.framework.vector.VectorStore;
import com.google.protobuf.Struct;
import com.google.protobuf.Value;
import io.pinecone.clients.Index;
import io.pinecone.clients.Pinecone;
import io.pinecone.unsigned_indices_model.VectorWithUnsignedIndices;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class PineconeVectorStore implements VectorStore {

    public static final String CONTENT_KEY = "__content";

    private final Pinecone client;
    private final Index    index;
    private final String   indexName;
    private final String   namespace;
    private final int      dimensions;

    private PineconeVectorStore(Pinecone client, Index index, String indexName,
                                String namespace, int dimensions) {

        this.client     = client;
        this.index      = index;
        this.indexName  = indexName;
        this.namespace  = namespace;
        this.dimensions = dimensions;
    }

    public static Builder builder() {

        return new Builder();
    }

    @Override
    public void upsert(List<VectorRecord> records) {

        if (records == null || records.isEmpty()) return;

        var vectors = new ArrayList<VectorWithUnsignedIndices>(records.size());
        for (var r : records) {

            if (r.vector().length != dimensions)
                throw new IllegalArgumentException(
                        "VectorRecord '" + r.id() + "' has " + r.vector().length
                      + " dimensions; index requires " + dimensions);

            var combined = new HashMap<String, String>(r.metadata().size() + 1);
            combined.putAll(r.metadata());
            combined.put(CONTENT_KEY, r.content());

            vectors.add(new VectorWithUnsignedIndices(
                    r.id(),
                    toFloatList(r.vector()),
                    toStruct(combined),
                    null));
        }

        index.upsert(vectors, namespace);
    }

    @Override
    public List<VectorHit> search(float[] queryVector, int k) {

        if (queryVector.length != dimensions)
            throw new IllegalArgumentException(
                    "Query vector has " + queryVector.length
                  + " dimensions; index requires " + dimensions);

        var response = index.queryByVector(
                k,
                toFloatList(queryVector),
                namespace,
                 false,
                 true);

        var hits = new ArrayList<VectorHit>();
        for (var match : response.getMatchesList()) {

            var meta = match.getMetadata();
            String              content  = "";
            Map<String, String> metadata = Map.of();

            if (meta != null) {
                var fields = meta.getFieldsMap();
                metadata = new HashMap<>();
                for (var e : fields.entrySet()) {
                    if (e.getKey().equals(CONTENT_KEY)) {
                        if (e.getValue().hasStringValue()) content = e.getValue().getStringValue();
                    }
                    else if (e.getValue().hasStringValue()) {
                        metadata.put(e.getKey(), e.getValue().getStringValue());
                    }
                }
            }

            hits.add(new VectorHit(match.getId(), match.getScore(), content, metadata));
        }
        return hits;
    }

    @Override
    public void delete(Collection<String> ids) {

        if (ids == null || ids.isEmpty()) return;

        index.deleteByIds(new ArrayList<>(ids), namespace);
    }

    @Override public int dimensions() { return dimensions; }

    public String indexName() { return indexName; }

    public String namespace() { return namespace; }

    private static Struct toStruct(Map<String, String> map) {

        var builder = Struct.newBuilder();
        map.forEach((k, v) -> builder.putFields(k, Value.newBuilder().setStringValue(v).build()));
        return builder.build();
    }

    private static List<Float> toFloatList(float[] v) {

        var out = new ArrayList<Float>(v.length);
        for (var f : v) out.add(f);
        return out;
    }

    public static final class Builder {

        private Pinecone injectedClient;
        private String   apiKey;
        private String   indexName;
        private String   namespace  = "";
        private int      dimensions = -1;

        public Builder client(Pinecone client) { this.injectedClient = client; return this; }

        public Builder apiKey(String apiKey)   { this.apiKey = apiKey; return this; }

        public Builder indexName(String name)  { this.indexName = name; return this; }

        public Builder namespace(String ns)    { this.namespace = ns; return this; }

        public Builder dimensions(int d)       { this.dimensions = d; return this; }

        public PineconeVectorStore build() {

            if (indexName == null || indexName.isBlank())
                throw new IllegalArgumentException("Pinecone indexName is required");

            if (dimensions <= 0)
                throw new IllegalArgumentException("Pinecone dimensions must be > 0");

            Pinecone client;
            if (injectedClient != null) {
                client = injectedClient;
            }
            else {
                if (apiKey == null || apiKey.isBlank())
                    throw new IllegalArgumentException("Pinecone apiKey is required");
                client = new Pinecone.Builder(apiKey).build();
            }

            var index = client.getIndexConnection(indexName);
            return new PineconeVectorStore(client, index, indexName,
                                           namespace == null ? "" : namespace, dimensions);
        }
    }
}
