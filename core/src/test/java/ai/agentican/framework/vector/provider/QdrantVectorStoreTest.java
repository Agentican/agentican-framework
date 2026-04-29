package ai.agentican.framework.vector.provider;

import ai.agentican.framework.vector.VectorStore;
import ai.agentican.framework.vector.VectorStoreContractTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

@Testcontainers(disabledWithoutDocker = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class QdrantVectorStoreTest extends VectorStoreContractTest {

    private static final DockerImageName QDRANT_IMAGE = DockerImageName.parse("qdrant/qdrant:v1.14.1");

    private final AtomicInteger counter = new AtomicInteger();
    private GenericContainer<?> qdrant;

    @BeforeAll
    void startContainer() {

        qdrant = new GenericContainer<>(QDRANT_IMAGE)
                .withExposedPorts(6333, 6334)
                .waitingFor(Wait.forHttp("/readyz").forPort(6333).forStatusCode(200));
        qdrant.start();
    }

    @AfterAll
    void stopContainer() {

        if (qdrant != null) qdrant.stop();
    }

    @Override
    protected VectorStore newStore(int dimensions) {

        var name = "vectors_" + counter.incrementAndGet() + "_"
                 + UUID.randomUUID().toString().replace("-", "").substring(0, 8);

        return QdrantVectorStore.builder()
                .host(qdrant.getHost())
                .port(qdrant.getMappedPort(6334))
                .collection(name)
                .dimensions(dimensions)
                .build();
    }
}
