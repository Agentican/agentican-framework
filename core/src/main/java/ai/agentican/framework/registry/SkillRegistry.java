package ai.agentican.framework.registry;

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

    default SkillConfig getByExternalId(String externalId) {

        if (externalId == null) return null;
        for (var skill : getAll())
            if (externalId.equals(skill.externalId())) return skill;
        return null;
    }

    Collection<SkillConfig> getAll();

    Map<String, SkillConfig> asMap();

    default void seed() { }

    default void delete(String ref) {

        throw new UnsupportedOperationException(
                getClass().getSimpleName() + " is read-only; delete not supported");
    }
}
