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

        var cfg = new AgentConfig("ag-" + Ids.generate(), "Researcher", "Investigates topics", "claude", null);
        var agent = Agent.builder().config(cfg).runner(NOOP_RUNNER).build();

        registry.register(agent);

        assertTrue(registry.isRegistered(cfg.id()));
        assertTrue(registry.isRegisteredByName("Researcher"));
        assertSame(agent, registry.get(cfg.id()));
    }

    @Test
    void seedRehydratesFromCatalogViaFactory() {

        var id = "ag-" + Ids.generate();
        var cfg = new AgentConfig(id, "Archivist", "Keeps records", "claude", "agent.archivist.v1");
        registry.register(Agent.builder().config(cfg).runner(NOOP_RUNNER).build());

        var fresh = new JpaAgentRegistry();
        fresh.seed(config -> Agent.builder().config(config).runner(NOOP_RUNNER).build());

        var rehydrated = fresh.get(id);
        assertNotNull(rehydrated, "seed() should have rehydrated the agent from the catalog");
        assertEquals("Archivist", rehydrated.name());
        assertEquals("claude", rehydrated.config().llm());
    }

    @Test
    void externalIdUpsertPreservesInternalIdAcrossDeploys() {

        var cfg1 = new AgentConfig(null, "Researcher", "Investigates topics", "claude", "researcher");
        registry.register(Agent.builder().config(cfg1).runner(NOOP_RUNNER).build());
        var firstInternalId = registry.getByExternalId("researcher").id();

        var cfg2 = new AgentConfig(null, "Researcher", "Investigates topics, v2", "claude", "researcher");
        registry.register(Agent.builder().config(cfg2).runner(NOOP_RUNNER).build());
        var secondInternalId = registry.getByExternalId("researcher").id();

        assertEquals(firstInternalId, secondInternalId,
                "External-id upsert must preserve the DB's internal id across deploys");
    }

    @Test
    void agentsWithoutExternalIdAreNotPersisted() {

        var agent = new Agent(
                AgentConfig.builder().id("ag-" + Ids.generate()).name("Ad-hoc").role("No catalog").build(),
                NOOP_RUNNER);
        registry.register(agent);

        assertTrue(registry.isRegistered(agent.id()));

        var fresh = new JpaAgentRegistry();
        fresh.seed(config -> Agent.builder().config(config).runner(NOOP_RUNNER).build());
        assertNull(fresh.get(agent.id()),
                "Agents without an externalId shouldn't appear in the catalog");
    }
}
