package ai.agentican.quarkus.store.rest;

import ai.agentican.framework.config.SkillConfig;
import ai.agentican.quarkus.store.rest.dto.RestSkillView;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RestSkillRegistryTest {

    private RestSkillRegistry registryWith(FakeRestCatalogClient client) {

        var registry = new RestSkillRegistry();
        registry.client = client;
        return registry;
    }

    @Test
    void seedPopulatesCacheFromCatalog() {

        var rows = List.of(
                new RestSkillView("s-1", "tone", "Use friendly tone", "ext.tone"),
                new RestSkillView("s-2", "brevity", "Be brief", null));

        var client = new FakeRestCatalogClient("[]", List.of(), rows);
        var registry = registryWith(client);

        registry.seed();

        assertEquals(2, registry.getAll().size());
        assertEquals("Use friendly tone", registry.getByName("tone").instructions());
        assertEquals("s-2", registry.getByName("brevity").id());
    }

    @Test
    void seedFailsFastOnNetworkError() {

        var client = new FakeRestCatalogClient("[]", List.of(), null) {
            @Override
            public List<RestSkillView> listSkills() {
                throw new RuntimeException("simulated catalog outage");
            }
        };

        var registry = registryWith(client);

        var ex = assertThrows(IllegalStateException.class, registry::seed);
        assertTrue(ex.getMessage().contains("Failed to seed skills"));
    }

    @Test
    void registerAddsLocalEntryAndReturnsIt() {

        var client = new FakeRestCatalogClient("[]", List.of(), List.of());
        var registry = registryWith(client);
        registry.seed();

        var skill = new SkillConfig("s-local", "local", "local instructions", "ext.local");

        assertSame(skill, registry.register(skill));
        assertEquals(skill, registry.get("s-local"));
        assertEquals(skill, registry.getByName("local"));
    }

    @Test
    void registerIfAbsentDedupesByExternalId() {

        var seeded = List.of(new RestSkillView("s-1", "shared", "v1", "ext.shared"));
        var client = new FakeRestCatalogClient("[]", List.of(), seeded);
        var registry = registryWith(client);
        registry.seed();

        var duplicate = new SkillConfig("s-2", "shared-variant", "v2", "ext.shared");

        var returned = registry.registerIfAbsent(duplicate);

        assertEquals("s-1", returned.id(), "Existing entry returned when externalId matches");
        assertEquals(1, registry.getAll().size());
    }
}
