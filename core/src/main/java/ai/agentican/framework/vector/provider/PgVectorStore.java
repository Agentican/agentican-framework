package ai.agentican.framework.vector.provider;

import ai.agentican.framework.util.Json;
import ai.agentican.framework.vector.VectorHit;
import ai.agentican.framework.vector.VectorRecord;
import ai.agentican.framework.vector.VectorStore;
import com.fasterxml.jackson.core.type.TypeReference;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public final class PgVectorStore implements VectorStore {

    private static final Pattern IDENT = Pattern.compile("^[A-Za-z_][A-Za-z0-9_]*$");

    private static final TypeReference<Map<String, String>> METADATA_TYPE = new TypeReference<>() {};

    public enum IndexStrategy {

        HNSW,
        IVFFLAT,
        NONE
    }

    private final DataSource dataSource;
    private final String     table;
    private final int        dimensions;

    private PgVectorStore(DataSource dataSource, String table, int dimensions) {

        this.dataSource = dataSource;
        this.table      = table;
        this.dimensions = dimensions;
    }

    public static Builder builder() {

        return new Builder();
    }

    @Override
    public void upsert(List<VectorRecord> records) {

        if (records == null || records.isEmpty()) return;

        var sql = "INSERT INTO " + table + " (id, embedding, content, metadata) "
                + "VALUES (?, ?::vector, ?, ?::jsonb) "
                + "ON CONFLICT (id) DO UPDATE SET "
                + "  embedding = EXCLUDED.embedding, "
                + "  content   = EXCLUDED.content, "
                + "  metadata  = EXCLUDED.metadata";

        try (var conn = dataSource.getConnection();
             var ps   = conn.prepareStatement(sql)) {

            for (var r : records) {

                if (r.vector().length != dimensions)
                    throw new IllegalArgumentException(
                            "VectorRecord '" + r.id() + "' has " + r.vector().length
                          + " dimensions; store requires " + dimensions);

                ps.setString(1, r.id());
                ps.setString(2, vectorLiteral(r.vector()));
                ps.setString(3, r.content());
                ps.setString(4, Json.mapper().writeValueAsString(r.metadata()));
                ps.addBatch();
            }
            ps.executeBatch();
        }
        catch (SQLException | com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new RuntimeException("PgVectorStore upsert failed: " + e.getMessage(), e);
        }
    }

    @Override
    public List<VectorHit> search(float[] queryVector, int k) {

        if (queryVector.length != dimensions)
            throw new IllegalArgumentException(
                    "Query vector has " + queryVector.length
                  + " dimensions; store requires " + dimensions);

        var sql = "SELECT id, content, metadata, "
                + "       1 - (embedding <=> ?::vector) AS score "
                + "  FROM " + table + " "
                + " ORDER BY embedding <=> ?::vector "
                + " LIMIT ?";

        try (var conn = dataSource.getConnection();
             var ps   = conn.prepareStatement(sql)) {

            var literal = vectorLiteral(queryVector);
            ps.setString(1, literal);
            ps.setString(2, literal);
            ps.setInt(3, k);

            try (var rs = ps.executeQuery()) {

                var hits = new ArrayList<VectorHit>();
                while (rs.next()) {

                    var meta = parseMetadata(rs.getString("metadata"));
                    hits.add(new VectorHit(
                            rs.getString("id"),
                            rs.getFloat("score"),
                            rs.getString("content"),
                            meta));
                }
                return hits;
            }
        }
        catch (SQLException e) {
            throw new RuntimeException("PgVectorStore search failed: " + e.getMessage(), e);
        }
    }

    @Override
    public void delete(Collection<String> ids) {

        if (ids == null || ids.isEmpty()) return;

        var sql = "DELETE FROM " + table + " WHERE id = ANY(?)";

        try (var conn = dataSource.getConnection();
             var ps   = conn.prepareStatement(sql)) {

            ps.setArray(1, conn.createArrayOf("text", ids.toArray()));
            ps.executeUpdate();
        }
        catch (SQLException e) {
            throw new RuntimeException("PgVectorStore delete failed: " + e.getMessage(), e);
        }
    }

    @Override public int dimensions() { return dimensions; }

    public String table() { return table; }

    private void ensureSchema(IndexStrategy index) {

        try (var conn = dataSource.getConnection();
             var stmt = conn.createStatement()) {

            stmt.execute("CREATE EXTENSION IF NOT EXISTS vector");

            stmt.execute(
                    "CREATE TABLE IF NOT EXISTS " + table + " ("
                  + "    id        TEXT PRIMARY KEY,"
                  + "    embedding VECTOR(" + dimensions + ") NOT NULL,"
                  + "    content   TEXT NOT NULL,"
                  + "    metadata  JSONB NOT NULL DEFAULT '{}'::jsonb"
                  + ")");

            switch (index) {
                case HNSW -> stmt.execute(
                        "CREATE INDEX IF NOT EXISTS " + table + "_embedding_idx "
                      + "ON " + table + " USING hnsw (embedding vector_cosine_ops)");
                case IVFFLAT -> stmt.execute(
                        "CREATE INDEX IF NOT EXISTS " + table + "_embedding_idx "
                      + "ON " + table + " USING ivfflat (embedding vector_cosine_ops) "
                      + "WITH (lists = 100)");
                case NONE -> { }
            }
        }
        catch (SQLException e) {
            throw new RuntimeException("PgVectorStore schema creation failed: " + e.getMessage(), e);
        }
    }

    private static String vectorLiteral(float[] v) {

        var sb = new StringBuilder(v.length * 8);
        sb.append('[');
        for (var i = 0; i < v.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(v[i]);
        }
        sb.append(']');
        return sb.toString();
    }

    private static Map<String, String> parseMetadata(String json) {

        if (json == null || json.isBlank()) return Map.of();
        try {
            var parsed = Json.mapper().readValue(json, METADATA_TYPE);
            return parsed == null ? Map.of() : new HashMap<>(parsed);
        }
        catch (Exception e) {
            throw new RuntimeException("PgVectorStore failed to parse metadata: " + json, e);
        }
    }

    public static final class Builder {

        private DataSource    dataSource;
        private String        table;
        private int           dimensions      = -1;
        private IndexStrategy indexStrategy   = IndexStrategy.HNSW;
        private boolean       createIfMissing = true;

        public Builder dataSource(DataSource ds) {

            this.dataSource = ds;
            return this;
        }

        public Builder table(String table) {

            this.table = table;
            return this;
        }

        public Builder dimensions(int dimensions) {

            this.dimensions = dimensions;
            return this;
        }

        public Builder index(IndexStrategy strategy) {

            this.indexStrategy = strategy;
            return this;
        }

        public Builder createIfMissing(boolean createIfMissing) {

            this.createIfMissing = createIfMissing;
            return this;
        }

        public PgVectorStore build() {

            if (dataSource == null)
                throw new IllegalArgumentException("dataSource is required");

            if (table == null || table.isBlank())
                throw new IllegalArgumentException("table is required");

            if (!IDENT.matcher(table).matches())
                throw new IllegalArgumentException(
                        "table must be a valid SQL identifier (letters, digits, underscore, "
                      + "starting with a letter or underscore): '" + table + "'");

            if (dimensions <= 0)
                throw new IllegalArgumentException("dimensions must be > 0");

            if (indexStrategy == null)
                throw new IllegalArgumentException("indexStrategy is required");

            var store = new PgVectorStore(dataSource, table, dimensions);
            if (createIfMissing) store.ensureSchema(indexStrategy);
            return store;
        }
    }
}
