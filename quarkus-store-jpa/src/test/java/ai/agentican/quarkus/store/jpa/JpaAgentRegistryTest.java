package ai.agentican.quarkus.store.jpa;

import ai.agentican.framework.agent.Agent;
import ai.agentican.framework.registry.AgentRegistry;
import ai.agentican.framework.agent.AgentRunner;
import ai.agentican.framework.config.AgentConfig;
import ai.agentican.framework.util.Ids;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class JpaAgentRegistryTest {

    @Inject
    JpaAgentRegistry registry;

    @Inject
    AgentRegistry registryInterface;

    private static final AgentRunner NOOP_RUNNER =
            (agent, task, taskId, stepId, stepName, timeout, skills, toolkits, outputSchema) -> null;

    @Test
    void interfaceResolvesToJpaBean() {

        assertSame(registry, registryInterface);
    }

    @Test
    void registerPersistsCatalogAndExposesInMemory() {

        var cfg = new AgentConfig("ag-" + Ids.generate(), "Researcher", "Investigates topics", "claude", null, null, null);
        var agent = Agent.builder().config(cfg).runner(NOOP_RUNNER).build();

        registry.register(agent);

        assertTrue(registry.hasById(cfg.id()));
        assertTrue(registry.hasByName("Researcher"));
        assertSame(agent, registry.byId(cfg.id()));
    }

    @Test
    void seedRehydratesFromCatalogViaFactory() {

        var id = "ag-" + Ids.generate();
        var cfg = new AgentConfig(id, "Archivist", "Keeps records", "claude", null, null, null);
        registry.register(Agent.builder().config(cfg).runner(NOOP_RUNNER).build());

        var fresh = new JpaAgentRegistry();
        fresh.agentFactory(config -> Agent.builder().config(config).runner(NOOP_RUNNER).build());
        fresh.seed();

        var rehydrated = fresh.byId(id);
        assertNotNull(rehydrated, "seed() should have rehydrated the agent from the catalog");
        assertEquals("Archivist", rehydrated.name());
        assertEquals("claude", rehydrated.config().llm());
    }

    @Test
    void registerSameIdUpdatesRowInPlace() {

        var id = "ag-" + Ids.generate();
        var first = new AgentConfig(id, "Researcher", "v1", "claude", null, null, null);
        registry.register(Agent.builder().config(first).runner(NOOP_RUNNER).build());

        var second = new AgentConfig(id, "Researcher", "v2", "claude", null, null, null);
        registry.register(Agent.builder().config(second).runner(NOOP_RUNNER).build());

        var fresh = new JpaAgentRegistry();
        fresh.agentFactory(config -> Agent.builder().config(config).runner(NOOP_RUNNER).build());
        fresh.seed();

        var rehydrated = fresh.byId(id);
        assertNotNull(rehydrated, "Re-registering with the same id should update, not duplicate");
        assertEquals("v2", rehydrated.config().role());
    }
}
