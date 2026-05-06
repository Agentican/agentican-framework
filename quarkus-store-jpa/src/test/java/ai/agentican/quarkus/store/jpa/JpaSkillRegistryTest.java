package ai.agentican.quarkus.store.jpa;

import ai.agentican.framework.config.SkillConfig;
import ai.agentican.framework.registry.SkillRegistry;
import ai.agentican.framework.util.Ids;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class JpaSkillRegistryTest {

    @Inject
    JpaSkillRegistry registry;

    @Inject
    SkillRegistry registryInterface;

    @Test
    void interfaceResolvesToJpaBean() {

        assertSame(registry, registryInterface);
    }

    @Test
    void registerPersistsAndExposesInMemory() {

        var skill = new SkillConfig("sk-" + Ids.generate(), "Summarize", "Condense a passage");

        registry.register(skill);

        assertTrue(registry.hasById(skill.id()));
        assertTrue(registry.hasByName("Summarize"));
        assertEquals(skill, registry.byId(skill.id()));
    }

    @Test
    void registerIfAbsentKeepsFirstWrite() {

        var id = "sk-" + Ids.generate();
        var first = new SkillConfig(id, "Outline", "first description");
        var second = new SkillConfig(id, "Outline", "second description");

        var stored = registry.registerIfAbsent(first);
        assertEquals(first, stored);

        var notOverwritten = registry.registerIfAbsent(second);
        assertEquals("first description", notOverwritten.instructions());
    }

    @Test
    void seedRehydratesFromCatalog() {

        var id = "sk-" + Ids.generate();
        registry.register(new SkillConfig(id, "Translate", "Translate between languages"));

        var fresh = new JpaSkillRegistry();
        fresh.seed();

        var rehydrated = fresh.byId(id);
        assertNotNull(rehydrated);
        assertEquals("Translate", rehydrated.name());
        assertEquals("Translate between languages", rehydrated.instructions());
    }
}
