package ai.agentican.framework.skill;

import ai.agentican.framework.config.SkillConfig;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class InMemorySkillRegistry implements SkillRegistry {

    private final ConcurrentMap<String, SkillConfig> byId = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, String> idByName = new ConcurrentHashMap<>();

    @Override
    public SkillConfig register(SkillConfig skill) {

        byId.put(skill.id(), skill);
        idByName.put(skill.name(), skill.id());
        return skill;
    }

    @Override
    public SkillConfig registerIfAbsent(SkillConfig skill) {

        var existing = byId.putIfAbsent(skill.id(), skill);

        if (existing == null) {
            idByName.putIfAbsent(skill.name(), skill.id());
            return skill;
        }

        return existing;
    }

    @Override
    public boolean isRegistered(String id) {

        return byId.containsKey(id);
    }

    @Override
    public boolean isRegisteredByName(String name) {

        return idByName.containsKey(name);
    }

    @Override
    public SkillConfig get(String id) {

        return byId.get(id);
    }

    @Override
    public SkillConfig getByName(String name) {

        var id = idByName.get(name);
        return id != null ? byId.get(id) : null;
    }

    @Override
    public Collection<SkillConfig> getAll() {

        return Collections.unmodifiableCollection(byId.values());
    }

    @Override
    public Map<String, SkillConfig> asMap() {

        return Collections.unmodifiableMap(byId);
    }
}
