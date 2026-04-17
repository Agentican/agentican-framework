package ai.agentican.quarkus.store.jpa;

import ai.agentican.framework.config.SkillConfig;
import ai.agentican.framework.skill.SkillRegistry;
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

        var skill = SkillConfig.of("sk-" + Ids.generate(), "Summarize", "Condense a passage");

        registry.register(skill);

        assertTrue(registry.isRegistered(skill.id()));
        assertTrue(registry.isRegisteredByName("Summarize"));
        assertEquals(skill, registry.get(skill.id()));
    }

    @Test
    void registerIfAbsentKeepsFirstWrite() {

        var id = "sk-" + Ids.generate();
        var first = SkillConfig.of(id, "Outline", "first description");
        var second = SkillConfig.of(id, "Outline", "second description");

        var stored = registry.registerIfAbsent(first);
        assertEquals(first, stored);

        var notOverwritten = registry.registerIfAbsent(second);
        assertEquals("first description", notOverwritten.instructions());
    }

    @Test
    void seedRehydratesFromCatalog() {

        var id = "sk-" + Ids.generate();
        registry.register(SkillConfig.of(id, "Translate", "Translate between languages"));

        var fresh = new JpaSkillRegistry();
        fresh.seed();

        var rehydrated = fresh.get(id);
        assertNotNull(rehydrated);
        assertEquals("Translate", rehydrated.name());
        assertEquals("Translate between languages", rehydrated.instructions());
    }
}
