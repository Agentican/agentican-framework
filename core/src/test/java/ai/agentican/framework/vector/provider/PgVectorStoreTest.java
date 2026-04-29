package ai.agentican.framework.vector.provider;

import ai.agentican.framework.vector.VectorRecord;
import ai.agentican.framework.vector.VectorStore;
import ai.agentican.framework.vector.VectorStoreContractTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers(disabledWithoutDocker = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PgVectorStoreTest extends VectorStoreContractTest {

    private static final DockerImageName PGVECTOR_IMAGE =
            DockerImageName.parse("pgvector/pgvector:pg16")
                           .asCompatibleSubstituteFor("postgres");

    private final AtomicInteger tableCounter = new AtomicInteger();
    private PostgreSQLContainer<?> postgres;
    private DataSource dataSource;

    @BeforeAll
    void startContainer() {

        postgres = new PostgreSQLContainer<>(PGVECTOR_IMAGE)
                .withDatabaseName("test")
                .withUsername("test")
                .withPassword("test");
        postgres.start();

        var ds = new PGSimpleDataSource();
        ds.setUrl(postgres.getJdbcUrl());
        ds.setUser(postgres.getUsername());
        ds.setPassword(postgres.getPassword());
        dataSource = ds;
    }

    @AfterAll
    void stopContainer() {

        if (postgres != null) postgres.stop();
    }

    @Override
    protected VectorStore newStore(int dimensions) {

        var table = "vectors_" + tableCounter.incrementAndGet() + "_"
                  + UUID.randomUUID().toString().replace("-", "").substring(0, 8);

        return PgVectorStore.builder()
                .dataSource(dataSource)
                .table(table)
                .dimensions(dimensions)
                .index(PgVectorStore.IndexStrategy.HNSW)
                .build();
    }

    @Test
    void rejectsInvalidTableNameAtBuild() {

        assertThrows(IllegalArgumentException.class, () -> PgVectorStore.builder()
                .dataSource(dataSource)
                .table("drop table users; --")
                .dimensions(3)
                .build());
    }

    @Test
    void upsertOverwritesByPrimaryKey() {

        var store = newStore(3);
        store.upsert(List.of(new VectorRecord(ID_A, new float[]{1f, 0f, 0f}, "v1", Map.of())));
        store.upsert(List.of(new VectorRecord(ID_A, new float[]{1f, 0f, 0f}, "v2", Map.of())));

        var hits = store.search(new float[]{1f, 0f, 0f}, 5);

        assertEquals(1,    hits.size());
        assertEquals("v2", hits.getFirst().content());
    }

    @Test
    void rejectsVectorOfWrongDimension() {

        var store = newStore(3);

        assertThrows(IllegalArgumentException.class,
                () -> store.upsert(List.of(
                        new VectorRecord(ID_A, new float[]{1f, 0f}, "x", Map.of()))));

        assertThrows(IllegalArgumentException.class,
                () -> store.search(new float[]{1f, 0f}, 1));
    }

    @Test
    void scoresAreHigherForBetterMatches() {

        var store = newStore(3);
        store.upsert(List.of(
                new VectorRecord(ID_A, new float[]{1f, 0f, 0f}, "near", Map.of()),
                new VectorRecord(ID_B, new float[]{0f, 1f, 0f}, "far",  Map.of())));

        var hits = store.search(new float[]{1f, 0f, 0f}, 2);

        assertTrue(hits.getFirst().score() > hits.get(1).score(),
                "Near match should score higher than far match");
    }
}
