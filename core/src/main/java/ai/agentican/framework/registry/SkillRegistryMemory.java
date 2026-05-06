package ai.agentican.framework.registry;

import ai.agentican.framework.config.SkillConfig;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class SkillRegistryMemory implements SkillRegistry {

    private final ConcurrentMap<String, SkillConfig> byId = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, SkillConfig> byName = new ConcurrentHashMap<>();

    @Override
    public SkillConfig register(SkillConfig skill) {

        byId.put(skill.id(), skill);
        byName.put(skill.name(), skill);

        return skill;
    }

    @Override
    public SkillConfig registerIfAbsent(SkillConfig skill) {

        var existing = byId.putIfAbsent(skill.id(), skill);

        if (existing == null) {

            byName.putIfAbsent(skill.name(), skill);

            return skill;
        }

        return existing;
    }

    @Override
    public boolean hasById(String id) {

        return byId.containsKey(id);
    }

    @Override
    public boolean hasByName(String name) {

        return byName.containsKey(name);
    }

    @Override
    public SkillConfig byId(String id) {

        return byId.get(id);
    }

    @Override
    public SkillConfig byName(String name) {

        return byName.get(name);
    }

    @Override
    public Collection<SkillConfig> list() {

        return Collections.unmodifiableCollection(byId.values());
    }

    @Override
    public Map<String, SkillConfig> asMap() {

        return Collections.unmodifiableMap(byId);
    }

    @Override
    public void delete(String ref) {

        var skill = byId.get(ref);

        if (skill == null) skill = byName.get(ref);

        if (skill == null) return;

        byId.remove(skill.id());
        byName.remove(skill.name());
    }
}
