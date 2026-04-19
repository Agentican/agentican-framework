package ai.agentican.framework.skill;

import ai.agentican.framework.config.SkillConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SkillRegistryTest {

    @Test
    void registerAndGet() {

        var registry = new InMemorySkillRegistry();
        var skill = new SkillConfig("summarize", "Summarize", "Summarize long text", null);

        registry.register(skill);

        assertSame(skill, registry.get("summarize"));
        assertTrue(registry.isRegistered("summarize"));
        assertFalse(registry.isRegistered("unknown"));
        assertSame(skill, registry.getByName("Summarize"));
    }

    @Test
    void registerIfAbsentIsFirstWins() {

        var registry = new InMemorySkillRegistry();
        var first = new SkillConfig("cite", "Cite Claims", "First version", null);
        var second = new SkillConfig("cite", "Cite Claims", "Second version", null);

        var kept = registry.registerIfAbsent(first);
        var rejected = registry.registerIfAbsent(second);

        assertSame(first, kept);
        assertSame(first, rejected);
        assertSame(first, registry.get("cite"));
    }

    @Test
    void getAllReturnsAllRegistered() {

        var registry = new InMemorySkillRegistry();

        registry.register(new SkillConfig("a", "A Skill", "A", null));
        registry.register(new SkillConfig("b", "B Skill", "B", null));

        assertEquals(2, registry.getAll().size());
        assertEquals(2, registry.asMap().size());
    }

    @Test
    void getUnknownReturnsNull() {

        var registry = new InMemorySkillRegistry();

        assertNull(registry.get("nope"));
        assertNull(registry.getByName("nope"));
    }
}
