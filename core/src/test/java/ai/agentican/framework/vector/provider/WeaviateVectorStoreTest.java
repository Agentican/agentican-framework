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
class WeaviateVectorStoreTest extends VectorStoreContractTest {

    private static final DockerImageName WEAVIATE_IMAGE =
            DockerImageName.parse("cr.weaviate.io/semitechnologies/weaviate:1.27.6");

    private final AtomicInteger counter = new AtomicInteger();
    private GenericContainer<?> weaviate;

    @BeforeAll
    void startContainer() {

        weaviate = new GenericContainer<>(WEAVIATE_IMAGE)
                .withExposedPorts(8080)
                .withEnv("AUTHENTICATION_ANONYMOUS_ACCESS_ENABLED", "true")
                .withEnv("PERSISTENCE_DATA_PATH", "/var/lib/weaviate")
                .withEnv("DEFAULT_VECTORIZER_MODULE", "none")
                .withEnv("CLUSTER_HOSTNAME", "node1")
                .waitingFor(Wait.forHttp("/v1/.well-known/ready").forPort(8080).forStatusCode(200));
        weaviate.start();
    }

    @AfterAll
    void stopContainer() {

        if (weaviate != null) weaviate.stop();
    }

    @Override
    protected VectorStore newStore(int dimensions) {

        var name = "Vectors" + counter.incrementAndGet() + "X"
                 + UUID.randomUUID().toString().replace("-", "").substring(0, 8);

        return WeaviateVectorStore.builder()
                .host(weaviate.getHost())
                .port(weaviate.getMappedPort(8080))
                .className(name)
                .dimensions(dimensions)
                .build();
    }
}
