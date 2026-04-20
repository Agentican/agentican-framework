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
    private final ConcurrentMap<String, String> idByExternalId = new ConcurrentHashMap<>();

    @Override
    @Transactional
    public SkillConfig register(SkillConfig skill) {

        var canonical = persistAndAlign(skill);

        byId.put(canonical.id(), canonical);
        idByName.put(canonical.name(), canonical.id());
        if (canonical.externalId() != null)
            idByExternalId.put(canonical.externalId(), canonical.id());

        return canonical;
    }

    @Override
    @Transactional
    public SkillConfig registerIfAbsent(SkillConfig skill) {

        if (skill.externalId() != null && idByExternalId.containsKey(skill.externalId()))
            return byId.get(idByExternalId.get(skill.externalId()));

        var existing = byId.putIfAbsent(skill.id(), skill);

        if (existing != null)
            return existing;

        var canonical = persistAndAlign(skill);

        if (!canonical.id().equals(skill.id())) {
            byId.remove(skill.id());
            byId.put(canonical.id(), canonical);
        }
        idByName.putIfAbsent(canonical.name(), canonical.id());
        if (canonical.externalId() != null)
            idByExternalId.putIfAbsent(canonical.externalId(), canonical.id());

        return canonical;
    }

    @Override
    @Transactional
    public void seed() {

        java.util.List<SkillEntity> rows = SkillEntity.listAll();

        for (var row : rows) {
            var cfg = new SkillConfig(row.id, row.name, row.instructions, row.externalId);
            byId.put(cfg.id(), cfg);
            idByName.put(cfg.name(), cfg.id());
            if (row.externalId != null)
                idByExternalId.put(row.externalId, cfg.id());
        }

        if (!rows.isEmpty())
            LOG.info("JpaSkillRegistry seeded {} skills from catalog", rows.size());
    }

    public SkillConfig getByExternalId(String externalId) {

        var id = idByExternalId.get(externalId);
        return id != null ? byId.get(id) : null;
    }

    @Override
    public boolean isRegistered(String id) { return byId.containsKey(id); }

    @Override
    public boolean isRegisteredByName(String name) { return idByName.containsKey(name); }

    @Override
    public SkillConfig get(String id) { return byId.get(id); }

    @Override
    public SkillConfig getByName(String name) {

        var id = idByName.get(name);
        return id != null ? byId.get(id) : null;
    }

    @Override
    public Collection<SkillConfig> getAll() { return Collections.unmodifiableCollection(byId.values()); }

    @Override
    public Map<String, SkillConfig> asMap() { return Collections.unmodifiableMap(byId); }

    private SkillConfig persistAndAlign(SkillConfig skill) {

        if (skill.externalId() != null) {

            var existing = (SkillEntity) SkillEntity.find("externalId", skill.externalId()).firstResult();

            if (existing != null) {

                existing.name = skill.name();
                existing.instructions = skill.instructions();
                existing.updatedAt = Instant.now();
                existing.persist();

                if (existing.id.equals(skill.id()))
                    return skill;

                return new SkillConfig(existing.id, skill.name(), skill.instructions(), skill.externalId());
            }

            var e = new SkillEntity();
            e.id = skill.id();
            e.externalId = skill.externalId();
            e.name = skill.name();
            e.instructions = skill.instructions();
            e.createdAt = Instant.now();
            e.updatedAt = e.createdAt;
            e.persist();
            return skill;
        }

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
        return skill;
    }
}
