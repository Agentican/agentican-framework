package ai.agentican.framework.vector.provider;

import ai.agentican.framework.vector.VectorHit;
import ai.agentican.framework.vector.VectorRecord;
import ai.agentican.framework.vector.VectorStore;
import io.qdrant.client.PointIdFactory;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.QdrantGrpcClient;
import io.qdrant.client.QueryFactory;
import io.qdrant.client.ValueFactory;
import io.qdrant.client.VectorsFactory;
import io.qdrant.client.WithPayloadSelectorFactory;
import io.qdrant.client.grpc.Collections;
import io.qdrant.client.grpc.JsonWithInt.Value;
import io.qdrant.client.grpc.Points;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class QdrantVectorStore implements VectorStore {

    private static final String CONTENT_KEY  = "content";

    private static final String METADATA_KEY = "metadata";

    private final QdrantClient client;
    private final String       collection;
    private final int          dimensions;

    private QdrantVectorStore(QdrantClient client, String collection, int dimensions) {

        this.client     = client;
        this.collection = collection;
        this.dimensions = dimensions;
    }

    public static Builder builder() {

        return new Builder();
    }

    @Override
    public void upsert(List<VectorRecord> records) {

        if (records == null || records.isEmpty()) return;

        var points = new ArrayList<Points.PointStruct>(records.size());
        for (var r : records) {

            if (r.vector().length != dimensions)
                throw new IllegalArgumentException(
                        "VectorRecord '" + r.id() + "' has " + r.vector().length
                      + " dimensions; collection requires " + dimensions);

            var metaFields = new HashMap<String, Value>();
            r.metadata().forEach((k, v) -> metaFields.put(k, ValueFactory.value(v)));

            points.add(Points.PointStruct.newBuilder()
                    .setId(PointIdFactory.id(UUID.fromString(r.id())))
                    .setVectors(VectorsFactory.vectors(toFloatList(r.vector())))
                    .putPayload(CONTENT_KEY,  ValueFactory.value(r.content()))
                    .putPayload(METADATA_KEY, ValueFactory.value(metaFields))
                    .build());
        }

        try {
            client.upsertAsync(collection, points).get();
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Qdrant upsert interrupted", e);
        }
        catch (Exception e) {
            throw new RuntimeException("Qdrant upsert failed: " + e.getMessage(), e);
        }
    }

    @Override
    public List<VectorHit> search(float[] queryVector, int k) {

        if (queryVector.length != dimensions)
            throw new IllegalArgumentException(
                    "Query vector has " + queryVector.length
                  + " dimensions; collection requires " + dimensions);

        var query = Points.QueryPoints.newBuilder()
                .setCollectionName(collection)
                .setQuery(QueryFactory.nearest(toFloatList(queryVector)))
                .setLimit(k)
                .setWithPayload(WithPayloadSelectorFactory.enable(true))
                .build();

        try {
            return client.queryAsync(query).get().stream().map(this::toHit).toList();
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Qdrant search interrupted", e);
        }
        catch (Exception e) {
            throw new RuntimeException("Qdrant search failed: " + e.getMessage(), e);
        }
    }

    @Override
    public void delete(Collection<String> ids) {

        if (ids == null || ids.isEmpty()) return;

        var pointIds = ids.stream()
                .map(id -> PointIdFactory.id(UUID.fromString(id)))
                .toList();

        try {
            client.deleteAsync(collection, pointIds).get();
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Qdrant delete interrupted", e);
        }
        catch (Exception e) {
            throw new RuntimeException("Qdrant delete failed: " + e.getMessage(), e);
        }
    }

    @Override public int dimensions() { return dimensions; }

    public String collection() { return collection; }

    private VectorHit toHit(Points.ScoredPoint p) {

        var pointId = p.getId();
        var id      = pointId.hasUuid() ? pointId.getUuid() : String.valueOf(pointId.getNum());

        var payload = p.getPayloadMap();

        var contentVal = payload.get(CONTENT_KEY);
        var content    = contentVal != null && contentVal.hasStringValue()
                ? contentVal.getStringValue() : "";

        Map<String, String> metadata = Map.of();
        var metaVal = payload.get(METADATA_KEY);
        if (metaVal != null && metaVal.hasStructValue()) {

            var fields = metaVal.getStructValue().getFieldsMap();
            metadata = new HashMap<>(fields.size());
            for (var e : fields.entrySet()) {
                if (e.getValue().hasStringValue())
                    metadata.put(e.getKey(), e.getValue().getStringValue());
            }
        }

        return new VectorHit(id, p.getScore(), content, metadata);
    }

    private void ensureCollection() {

        try {
            if (client.collectionExistsAsync(collection).get()) return;

            client.createCollectionAsync(collection,
                    Collections.VectorParams.newBuilder()
                            .setSize(dimensions)
                            .setDistance(Collections.Distance.Cosine)
                            .build()).get();
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Qdrant collection setup interrupted", e);
        }
        catch (Exception e) {
            throw new RuntimeException("Qdrant collection setup failed: " + e.getMessage(), e);
        }
    }

    private static List<Float> toFloatList(float[] v) {

        var out = new ArrayList<Float>(v.length);
        for (var f : v) out.add(f);
        return out;
    }

    public static final class Builder {

        private String       host            = "localhost";
        private int          port            = 6334;
        private boolean      useTls          = false;
        private String       apiKey;
        private String       collection;
        private int          dimensions      = -1;
        private Duration     timeout         = Duration.ofSeconds(30);
        private boolean      createIfMissing = true;
        private QdrantClient injectedClient;

        public Builder host(String host)              { this.host = host; return this; }

        public Builder port(int port)                 { this.port = port; return this; }

        public Builder useTls(boolean useTls)         { this.useTls = useTls; return this; }

        public Builder apiKey(String apiKey)          { this.apiKey = apiKey; return this; }

        public Builder collection(String c)           { this.collection = c; return this; }

        public Builder dimensions(int d)              { this.dimensions = d; return this; }

        public Builder timeout(Duration t)            { this.timeout = t; return this; }

        public Builder createIfMissing(boolean v)     { this.createIfMissing = v; return this; }

        public Builder client(QdrantClient c)         { this.injectedClient = c; return this; }

        public QdrantVectorStore build() {

            if (collection == null || collection.isBlank())
                throw new IllegalArgumentException("Qdrant collection name is required");

            if (dimensions <= 0)
                throw new IllegalArgumentException("Qdrant dimensions must be > 0");

            QdrantClient client;
            if (injectedClient != null) {
                client = injectedClient;
            }
            else {
                var grpc = QdrantGrpcClient.newBuilder(host, port, useTls);
                if (apiKey != null && !apiKey.isBlank()) grpc.withApiKey(apiKey);
                grpc.withTimeout(timeout);
                client = new QdrantClient(grpc.build());
            }

            var store = new QdrantVectorStore(client, collection, dimensions);
            if (createIfMissing) store.ensureCollection();
            return store;
        }
    }
}
