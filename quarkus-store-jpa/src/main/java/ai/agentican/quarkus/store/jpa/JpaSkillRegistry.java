package ai.agentican.quarkus.store.jpa;

import ai.agentican.framework.config.SkillConfig;
import ai.agentican.framework.registry.SkillRegistry;
import ai.agentican.quarkus.store.jpa.entity.SkillEntity;

import io.quarkus.arc.properties.IfBuildProperty;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@ApplicationScoped
@IfBuildProperty(name = "agentican.store.backend", stringValue = "jpa", enableIfMissing = true)
public class JpaSkillRegistry implements SkillRegistry {

    private static final Logger LOG = LoggerFactory.getLogger(JpaSkillRegistry.class);

    private final ConcurrentMap<String, SkillConfig> byId = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, String> idByName = new ConcurrentHashMap<>();

    @Override
    @Transactional
    public SkillConfig register(SkillConfig skill) {

        persist(skill);

        byId.put(skill.id(), skill);
        idByName.put(skill.name(), skill.id());

        return skill;
    }

    @Override
    @Transactional
    public SkillConfig registerIfAbsent(SkillConfig skill) {

        var existing = byId.putIfAbsent(skill.id(), skill);
        if (existing != null) return existing;

        persist(skill);
        idByName.putIfAbsent(skill.name(), skill.id());

        return skill;
    }

    @Override
    @Transactional
    public void seed() {

        java.util.List<SkillEntity> rows = SkillEntity.listAll();

        for (var row : rows) {
            var cfg = new SkillConfig(row.id, row.name, row.instructions);
            byId.put(cfg.id(), cfg);
            idByName.put(cfg.name(), cfg.id());
        }

        if (!rows.isEmpty())
            LOG.info("JpaSkillRegistry seeded {} skills from catalog", rows.size());
    }

    @Override
    public boolean hasById(String id) { return byId.containsKey(id); }

    @Override
    public boolean hasByName(String name) { return idByName.containsKey(name); }

    @Override
    public SkillConfig byId(String id) { return byId.get(id); }

    @Override
    public SkillConfig byName(String name) {

        var id = idByName.get(name);
        return id != null ? byId.get(id) : null;
    }

    @Override
    public Collection<SkillConfig> list() { return Collections.unmodifiableCollection(byId.values()); }

    @Override
    public Map<String, SkillConfig> asMap() { return Collections.unmodifiableMap(byId); }

    @Override
    @Transactional
    public void delete(String ref) {

        var skill = resolve(ref);

        if (skill == null) {
            LOG.debug("delete('{}'): no skill registered under this ref", ref);
            return;
        }

        SkillEntity.deleteById(skill.id());

        byId.remove(skill.id());
        idByName.remove(skill.name());

        LOG.info("Skill '{}' (id={}) deleted from catalog", skill.name(), skill.id());
    }

    private SkillConfig resolve(String ref) {

        var byIdHit = byId.get(ref);
        if (byIdHit != null) return byIdHit;

        return byName(ref);
    }

    private void persist(SkillConfig skill) {

        var existing = (SkillEntity) SkillEntity.findById(skill.id());
        var e = existing != null ? existing : new SkillEntity();

        if (existing == null) {
            e.id = skill.id();
            e.createdAt = Instant.now();
        }

        e.name = skill.name();
        e.instructions = skill.instructions();
        e.updatedAt = Instant.now();

        e.persist();
    }
}
