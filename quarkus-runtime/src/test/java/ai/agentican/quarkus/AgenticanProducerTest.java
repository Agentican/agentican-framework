package ai.agentican.quarkus;

import ai.agentican.framework.Agentican;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@QuarkusTest
class AgenticanProducerTest {

    @Inject
    Agentican agentican;

    @Inject
    ai.agentican.framework.config.RuntimeConfig runtimeConfig;

    @Test
    void agenticanIsProducedAsApplicationScopedBean() {

        assertNotNull(agentican, "Agentican bean should be injected");
        assertNotNull(agentican.registry().agents(), "Agent registry should be initialized");
    }

    @Test
    void runtimeConfigBindsLlmFromYaml() {

        assertEquals(1, runtimeConfig.llm().size());

        var llm = runtimeConfig.llm().getFirst();

        assertEquals("default", llm.name());
        assertEquals("anthropic", llm.provider());
        assertEquals("test-key", llm.apiKey());
        assertEquals("claude-sonnet-4-5", llm.model());
    }

    @Test
    void runtimeConfigBindsAgentRunnerFromYaml() {

        var runner = runtimeConfig.agentRunner();

        assertNotNull(runner);
        assertEquals(15, runner.maxTurns());
    }

    @Test
    void preRegisteredAgentsAreLoadedFromYamlIntoRegistry() {

        var registered = agentican.registry().agents().byName("researcher");

        assertNotNull(registered, "Agent loaded from agentican.yaml should be registered");
        assertEquals("Expert at finding information", registered.role());
    }
}
