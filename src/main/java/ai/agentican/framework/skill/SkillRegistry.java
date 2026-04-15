package ai.agentican.framework.skill;

import ai.agentican.framework.config.SkillConfig;

import java.util.Collection;
import java.util.Map;

public interface SkillRegistry {

    SkillConfig register(SkillConfig skill);

    SkillConfig registerIfAbsent(SkillConfig skill);

    boolean isRegistered(String id);

    boolean isRegisteredByName(String name);

    SkillConfig get(String id);

    SkillConfig getByName(String name);

    Collection<SkillConfig> getAll();

    Map<String, SkillConfig> asMap();

    default void seed() { }
}
