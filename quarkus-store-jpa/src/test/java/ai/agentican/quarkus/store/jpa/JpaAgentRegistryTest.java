package ai.agentican.quarkus.store.jpa;

import ai.agentican.framework.agent.Agent;
import ai.agentican.framework.agent.AgentRegistry;
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
            (agent, task, activeSkills, toolkits, taskId, stepId, stepName) -> null;

    @Test
    void interfaceResolvesToJpaBean() {

        assertSame(registry, registryInterface);
    }

    @Test
    void registerPersistsCatalogAndExposesInMemory() {

        var cfg = AgentConfig.of("ag-" + Ids.generate(), "Researcher", "Investigates topics", "claude");
        var agent = Agent.of(cfg, NOOP_RUNNER);

        registry.register(agent);

        assertTrue(registry.isRegistered(cfg.id()));
        assertTrue(registry.isRegisteredByName("Researcher"));
        assertSame(agent, registry.get(cfg.id()));
    }

    @Test
    void seedRehydratesFromCatalogViaFactory() {

        var id = "ag-" + Ids.generate();
        var cfg = AgentConfig.of(id, "Archivist", "Keeps records", "claude");
        registry.register(Agent.of(cfg, NOOP_RUNNER));

        var fresh = new JpaAgentRegistry();
        fresh.seed(config -> Agent.of(config, NOOP_RUNNER));

        var rehydrated = fresh.get(id);
        assertNotNull(rehydrated, "seed() should have rehydrated the agent from the catalog");
        assertEquals("Archivist", rehydrated.name());
        assertEquals("claude", rehydrated.config().llm());
    }

    @Test
    void externalIdUpsertPreservesInternalIdAcrossDeploys() {

        var cfg1 = AgentConfig.forCatalog("researcher", "Researcher", "Investigates topics", "claude");
        registry.register(Agent.of(cfg1, NOOP_RUNNER));
        var firstInternalId = registry.getByExternalId("researcher").id();

        var cfg2 = AgentConfig.forCatalog("researcher", "Researcher", "Investigates topics, v2", "claude");
        registry.register(Agent.of(cfg2, NOOP_RUNNER));
        var secondInternalId = registry.getByExternalId("researcher").id();

        assertEquals(firstInternalId, secondInternalId,
                "External-id upsert must preserve the DB's internal id across deploys");
    }

    @Test
    void customRunnerAgentsWithoutConfigAreNotPersisted() {

        var agent = Agent.of("ag-" + Ids.generate(), "Ad-hoc", "No config", NOOP_RUNNER);
        registry.register(agent);

        assertTrue(registry.isRegistered(agent.id()));

        var fresh = new JpaAgentRegistry();
        fresh.seed(config -> Agent.of(config, NOOP_RUNNER));
        assertNull(fresh.get(agent.id()),
                "Agents registered without AgentConfig shouldn't appear in the catalog");
    }
}
