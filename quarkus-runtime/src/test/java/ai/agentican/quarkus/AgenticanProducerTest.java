package ai.agentican.quarkus;

import ai.agentican.framework.AgenticanRuntime;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@QuarkusTest
class AgenticanProducerTest {

    @Inject
    AgenticanRuntime agentican;

    @Inject
    AgenticanConfig config;

    @Test
    void agenticanIsProducedAsApplicationScopedBean() {

        assertNotNull(agentican, "AgenticanRuntime bean should be injected");
        assertNotNull(agentican.registry().agents(), "Agent registry should be initialized");
    }

    @Test
    void configMappingBindsLlm() {

        assertEquals(1, config.llm().size());

        var llm = config.llm().getFirst();

        assertEquals("default", llm.name());
        assertEquals("anthropic", llm.provider());
        assertEquals("test-key", llm.apiKey());
        assertEquals("claude-sonnet-4-5", llm.model().orElseThrow());
    }

    @Test
    void configMappingBindsAgentRunnerDefaults() {

        var runner = config.agentRunner().orElseThrow();

        assertEquals(15, runner.maxTurns());
    }

    @Test
    void configMappingBindsPreRegisteredAgents() {

        assertEquals(1, config.agents().size());

        var researcher = config.agents().getFirst();

        assertEquals("researcher", researcher.name());
        assertEquals("Expert at finding information", researcher.role());
    }

    @Test
    void preRegisteredAgentsAreAvailableInRegistry() {

        assertNotNull(agentican.registry().agents().getByName("researcher"));
    }
}
