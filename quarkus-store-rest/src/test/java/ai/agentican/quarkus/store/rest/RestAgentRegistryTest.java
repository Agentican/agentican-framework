package ai.agentican.quarkus.store.rest;

import ai.agentican.framework.agent.Agent;
import ai.agentican.framework.agent.AgentRunner;
import ai.agentican.framework.config.AgentConfig;
import ai.agentican.quarkus.store.rest.dto.RestAgentView;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RestAgentRegistryTest {

    private static final AgentRunner DUMMY_RUNNER = (agent, task, taskId, stepId, stepName, timeout,
                                                     skills, toolkits, outputSchema) -> null;

    private RestAgentRegistry registryWith(FakeRestCatalogClient client) {

        var registry = new RestAgentRegistry();
        registry.client = client;
        return registry;
    }

    @Test
    void seedUsesFactoryToBuildAgents() {

        var rows = List.of(
                new RestAgentView("a-1", "Analyst", "Market analyst", "default"),
                new RestAgentView("a-2", "Writer",  "Copywriter",     "default"));

        var client = new FakeRestCatalogClient("[]", rows, List.of());
        var registry = registryWith(client);

        registry.agentFactory(cfg -> new Agent(cfg, DUMMY_RUNNER));
        registry.seed();

        assertEquals(2, registry.list().size());
        assertEquals("Analyst", registry.byName("Analyst").name());
        assertEquals("Market analyst", registry.byName("Analyst").role());
        assertEquals("default", registry.byName("Analyst").config().llm());
    }

    @Test
    void seedRequiresFactory() {

        var registry = registryWith(new FakeRestCatalogClient("[]", List.of(), List.of()));

        assertThrows(IllegalStateException.class, registry::seed);
    }

    @Test
    void seedFailsFastOnNetworkError() {

        var client = new FakeRestCatalogClient("[]", null, List.of()) {
            @Override
            public List<RestAgentView> listAgents() {
                throw new RuntimeException("simulated catalog outage");
            }
        };

        var registry = registryWith(client);
        registry.agentFactory(cfg -> new Agent(cfg, DUMMY_RUNNER));

        var ex = assertThrows(IllegalStateException.class, registry::seed);
        assertTrue(ex.getMessage().contains("Failed to seed agents"));
    }

    @Test
    void registerAddsLocalAgent() {

        var registry = registryWith(new FakeRestCatalogClient("[]", List.of(), List.of()));
        registry.agentFactory(cfg -> new Agent(cfg, DUMMY_RUNNER));
        registry.seed();

        var cfg = new AgentConfig("a-local", "Local", "Local agent", "default", null, null, null);
        var agent = new Agent(cfg, DUMMY_RUNNER);

        registry.register(agent);

        assertEquals(agent, registry.byId("a-local"));
        assertEquals(agent, registry.byName("Local"));
    }
}
