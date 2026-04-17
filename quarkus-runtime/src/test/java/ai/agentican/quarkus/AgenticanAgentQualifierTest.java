package ai.agentican.quarkus;

import ai.agentican.framework.agent.Agent;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@QuarkusTest
class AgenticanAgentQualifierTest {

    @Inject
    @AgenticanAgent("researcher")
    Agent researcher;

    @Test
    void qualifierResolvesAgentByName() {

        assertNotNull(researcher);
        assertEquals("researcher", researcher.name());
    }
}
