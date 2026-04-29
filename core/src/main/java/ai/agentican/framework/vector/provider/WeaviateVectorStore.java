package ai.agentican.framework.vector.provider;

import ai.agentican.framework.util.Json;
import ai.agentican.framework.vector.VectorHit;
import ai.agentican.framework.vector.VectorRecord;
import ai.agentican.framework.vector.VectorStore;
import com.fasterxml.jackson.core.type.TypeReference;
import io.weaviate.client.Config;
import io.weaviate.client.WeaviateClient;
import io.weaviate.client.base.Result;
import io.weaviate.client.v1.data.model.WeaviateObject;
import io.weaviate.client.v1.graphql.query.argument.NearVectorArgument;
import io.weaviate.client.v1.graphql.query.fields.Field;
import io.weaviate.client.v1.misc.model.VectorIndexConfig;
import io.weaviate.client.v1.schema.model.Property;
import io.weaviate.client.v1.schema.model.WeaviateClass;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public final class WeaviateVectorStore implements VectorStore {

    private static final Pattern CLASS_NAME = Pattern.compile("^[A-Z][A-Za-z0-9_]{0,62}$");

    private static final TypeReference<Map<String, String>> METADATA_TYPE = new TypeReference<>() {};

    private final WeaviateClient client;
    private final String         className;
    private final int            dimensions;

    private WeaviateVectorStore(WeaviateClient client, String className, int dimensions) {

        this.client     = client;
        this.className  = className;
        this.dimensions = dimensions;
    }

    public static Builder builder() {

        return new Builder();
    }

    @Override
    public void upsert(List<VectorRecord> records) {

        if (records == null || records.isEmpty()) return;

        var objects = new ArrayList<WeaviateObject>(records.size());
        for (var r : records) {

            if (r.vector().length != dimensions)
                throw new IllegalArgumentException(
                        "VectorRecord '" + r.id() + "' has " + r.vector().length
                      + " dimensions; class requires " + dimensions);

            String metaJson;
            try { metaJson = Json.writeValueAsString(r.metadata()); }
            catch (Exception e) {
                throw new RuntimeException("Failed to serialize metadata for " + r.id(), e);
            }

            objects.add(WeaviateObject.builder()
                    .className(className)
                    .id(r.id())
                    .vector(toFloatBoxed(r.vector()))
                    .properties(Map.of(
                            "content",       r.content(),
                            "metadata_json", metaJson))
                    .build());
        }

        var result = client.batch().objectsBatcher().withObjects(objects.toArray(new WeaviateObject[0])).run();
        checkBatchResult(result);
    }

    @Override
    public List<VectorHit> search(float[] queryVector, int k) {

        if (queryVector.length != dimensions)
            throw new IllegalArgumentException(
                    "Query vector has " + queryVector.length
                  + " dimensions; class requires " + dimensions);

        var nearVector = NearVectorArgument.builder()
                .vector(toFloatBoxed(queryVector))
                .build();

        var fields = new Field[]{
                Field.builder().name("content").build(),
                Field.builder().name("metadata_json").build(),
                Field.builder().name("_additional").fields(new Field[]{
                        Field.builder().name("id").build(),
                        Field.builder().name("certainty").build(),
                        Field.builder().name("distance").build()
                }).build()};

        var result = client.graphQL().get()
                .withClassName(className)
                .withFields(fields)
                .withNearVector(nearVector)
                .withLimit(k)
                .run();

        if (result.hasErrors())
            throw new RuntimeException("Weaviate GraphQL error: " + result.getError());

        return parseSearchResult(result.getResult().getData());
    }

    @Override
    public void delete(Collection<String> ids) {

        if (ids == null || ids.isEmpty()) return;

        for (var id : ids) {

            var result = client.data().deleter()
                    .withClassName(className)
                    .withID(id)
                    .run();

            if (result.hasErrors() && !is404(result))
                throw new RuntimeException("Weaviate delete " + id + " failed: " + result.getError());
        }
    }

    @Override public int dimensions() { return dimensions; }

    public String className() { return className; }

    private void ensureClass() {

        var existsResult = client.schema().exists().withClassName(className).run();
        if (existsResult.hasErrors())
            throw new RuntimeException("Weaviate class-exists check failed: " + existsResult.getError());

        if (Boolean.TRUE.equals(existsResult.getResult())) return;

        var clazz = WeaviateClass.builder()
                .className(className)
                .vectorizer("none")
                .vectorIndexType("hnsw")
                .vectorIndexConfig(VectorIndexConfig.builder().distance("cosine").build())
                .properties(List.of(
                        Property.builder().name("content")      .dataType(List.of("text")).build(),
                        Property.builder().name("metadata_json").dataType(List.of("text")).build()))
                .build();

        var createResult = client.schema().classCreator().withClass(clazz).run();
        if (createResult.hasErrors())
            throw new RuntimeException("Weaviate class creation failed: " + createResult.getError());
    }

    @SuppressWarnings("unchecked")
    private List<VectorHit> parseSearchResult(Object data) {

        if (data == null) return List.of();

        try {
            var dataMap = (Map<String, Object>) data;
            var get     = (Map<String, Object>) dataMap.get("Get");
            if (get == null) return List.of();

            var entries = (List<Map<String, Object>>) get.get(className);
            if (entries == null) return List.of();

            var hits = new ArrayList<VectorHit>(entries.size());
            for (var entry : entries) {

                var content    = (String) entry.getOrDefault("content", "");
                var metaString = (String) entry.get("metadata_json");

                Map<String, String> metadata = Map.of();
                if (metaString != null && !metaString.isBlank()) {
                    var parsed = Json.mapper().readValue(metaString, METADATA_TYPE);
                    metadata = parsed == null ? Map.of() : new HashMap<>(parsed);
                }

                String id    = "";
                float  score = 0f;
                var additional = (Map<String, Object>) entry.get("_additional");
                if (additional != null) {
                    if (additional.get("id") instanceof String s) id = s;
                    if (additional.get("certainty") instanceof Number n) score = n.floatValue();
                }

                hits.add(new VectorHit(id, score, content, metadata));
            }
            return hits;
        }
        catch (Exception e) {
            throw new RuntimeException("Weaviate failed to parse search response: " + e.getMessage(), e);
        }
    }

    private static void checkBatchResult(Result<io.weaviate.client.v1.batch.model.ObjectGetResponse[]> result) {

        if (result.hasErrors())
            throw new RuntimeException("Weaviate batch upsert failed: " + result.getError());

        var responses = result.getResult();
        if (responses == null) return;

        for (var resp : responses) {
            var objResult = resp.getResult();
            if (objResult != null && objResult.getStatus() != null
                    && objResult.getStatus().equalsIgnoreCase("FAILED")) {
                var errors = objResult.getErrors();
                throw new RuntimeException("Weaviate object upsert failed: "
                        + (errors != null ? errors.toString() : "unknown error"));
            }
        }
    }

    private static boolean is404(Result<?> result) {

        var error = result.getError();
        return error != null && error.getStatusCode() == 404;
    }

    private static Float[] toFloatBoxed(float[] v) {

        var out = new Float[v.length];
        for (var i = 0; i < v.length; i++) out[i] = v[i];
        return out;
    }

    public static final class Builder {

        private String         scheme          = "http";
        private String         host;
        private int            port            = 8080;
        private String         apiKey;
        private String         className;
        private int            dimensions      = -1;
        private boolean        createIfMissing = true;
        private WeaviateClient injectedClient;

        public Builder scheme(String scheme)          { this.scheme = scheme; return this; }

        public Builder host(String host)              { this.host = host; return this; }

        public Builder port(int port)                 { this.port = port; return this; }

        public Builder apiKey(String apiKey)          { this.apiKey = apiKey; return this; }

        public Builder className(String name)         { this.className = name; return this; }

        public Builder dimensions(int dimensions)     { this.dimensions = dimensions; return this; }

        public Builder createIfMissing(boolean value) { this.createIfMissing = value; return this; }

        public Builder client(WeaviateClient c)       { this.injectedClient = c; return this; }

        public WeaviateVectorStore build() {

            if (className == null || className.isBlank())
                throw new IllegalArgumentException("Weaviate className is required");

            if (!CLASS_NAME.matcher(className).matches())
                throw new IllegalArgumentException(
                        "Weaviate className must match [A-Z][A-Za-z0-9_]{0,62} "
                      + "(GraphQL type-name rules): '" + className + "'");

            if (dimensions <= 0)
                throw new IllegalArgumentException("Weaviate dimensions must be > 0");

            WeaviateClient client;
            if (injectedClient != null) {
                client = injectedClient;
            }
            else {
                if (host == null || host.isBlank())
                    throw new IllegalArgumentException("Weaviate host is required");

                Map<String, String> headers = (apiKey != null && !apiKey.isBlank())
                        ? Map.of("Authorization", "Bearer " + apiKey)
                        : Map.of();

                client = new WeaviateClient(new Config(scheme, host + ":" + port, headers));
            }

            var store = new WeaviateVectorStore(client, className, dimensions);
            if (createIfMissing) store.ensureClass();
            return store;
        }
    }
}
