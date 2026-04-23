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
                new RestAgentView("a-1", "Analyst", "Market analyst", "default", "ext.analyst"),
                new RestAgentView("a-2", "Writer",  "Copywriter",     "default", null));

        var client = new FakeRestCatalogClient("[]", rows, List.of());
        var registry = registryWith(client);

        registry.seed(cfg -> new Agent(cfg, DUMMY_RUNNER));

        assertEquals(2, registry.getAll().size());
        assertEquals("Analyst", registry.getByName("Analyst").name());
        assertEquals("Market analyst", registry.getByName("Analyst").role());
        assertEquals("default", registry.getByName("Analyst").config().llm());
    }

    @Test
    void seedRequiresFactory() {

        var registry = registryWith(new FakeRestCatalogClient("[]", List.of(), List.of()));

        assertThrows(IllegalArgumentException.class, () -> registry.seed(null));
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

        var ex = assertThrows(IllegalStateException.class,
                () -> registry.seed(cfg -> new Agent(cfg, DUMMY_RUNNER)));
        assertTrue(ex.getMessage().contains("Failed to seed agents"));
    }

    @Test
    void registerAddsLocalAgent() {

        var registry = registryWith(new FakeRestCatalogClient("[]", List.of(), List.of()));
        registry.seed(cfg -> new Agent(cfg, DUMMY_RUNNER));

        var cfg = new AgentConfig("a-local", "Local", "Local agent", "default", "ext.local");
        var agent = new Agent(cfg, DUMMY_RUNNER);

        registry.register(agent);

        assertEquals(agent, registry.get("a-local"));
        assertEquals(agent, registry.getByName("Local"));
    }
}
