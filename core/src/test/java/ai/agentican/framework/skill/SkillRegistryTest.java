package ai.agentican.framework.skill;

import ai.agentican.framework.config.SkillConfig;
import ai.agentican.framework.registry.SkillRegistryMemory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SkillRegistryTest {

    @Test
    void registerAndGet() {

        var registry = new SkillRegistryMemory();
        var skill = new SkillConfig("summarize", "Summarize", "Summarize long text");

        registry.register(skill);

        assertSame(skill, registry.byId("summarize"));
        assertTrue(registry.hasById("summarize"));
        assertFalse(registry.hasById("unknown"));
        assertSame(skill, registry.byName("Summarize"));
    }

    @Test
    void registerIfAbsentIsFirstWins() {

        var registry = new SkillRegistryMemory();
        var first = new SkillConfig("cite", "Cite Claims", "First version");
        var second = new SkillConfig("cite", "Cite Claims", "Second version");

        var kept = registry.registerIfAbsent(first);
        var rejected = registry.registerIfAbsent(second);

        assertSame(first, kept);
        assertSame(first, rejected);
        assertSame(first, registry.byId("cite"));
    }

    @Test
    void getAllReturnsAllRegistered() {

        var registry = new SkillRegistryMemory();

        registry.register(new SkillConfig("a", "A Skill", "A"));
        registry.register(new SkillConfig("b", "B Skill", "B"));

        assertEquals(2, registry.list().size());
        assertEquals(2, registry.asMap().size());
    }

    @Test
    void getUnknownReturnsNull() {

        var registry = new SkillRegistryMemory();

        assertNull(registry.byId("nope"));
        assertNull(registry.byName("nope"));
    }
}
