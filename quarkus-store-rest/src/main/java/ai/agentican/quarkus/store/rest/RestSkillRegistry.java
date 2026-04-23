package ai.agentican.quarkus.store.rest;

import ai.agentican.framework.config.SkillConfig;
import ai.agentican.framework.registry.SkillRegistry;

import io.quarkus.arc.properties.IfBuildProperty;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@ApplicationScoped
@IfBuildProperty(name = "agentican.store.backend", stringValue = "rest")
public class RestSkillRegistry implements SkillRegistry {

    private static final Logger LOG = LoggerFactory.getLogger(RestSkillRegistry.class);

    private final ConcurrentMap<String, SkillConfig> byId = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, String> idByName = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, String> idByExternalId = new ConcurrentHashMap<>();

    @Inject
    @RestClient
    RestCatalogClient client;

    @Override
    public void seed() {

        try {

            var rows = client.listSkills();

            if (rows == null) return;

            for (var row : rows) {

                var cfg = new SkillConfig(row.id(), row.name(), row.instructions(), row.externalId());

                byId.put(cfg.id(), cfg);
                idByName.put(cfg.name(), cfg.id());

                if (cfg.externalId() != null)
                    idByExternalId.put(cfg.externalId(), cfg.id());
            }

            if (!rows.isEmpty())
                LOG.info("RestSkillRegistry seeded {} skills from catalog", rows.size());
        }
        catch (Exception e) {

            throw new IllegalStateException(
                    "Failed to seed skills from REST catalog (check quarkus.rest-client.agentican-catalog.url): "
                            + e.getMessage(), e);
        }
    }

    @Override
    public SkillConfig register(SkillConfig skill) {

        byId.put(skill.id(), skill);
        idByName.put(skill.name(), skill.id());

        if (skill.externalId() != null)
            idByExternalId.put(skill.externalId(), skill.id());

        LOG.debug("Registered skill '{}' locally (not persisted to central catalog)", skill.name());
        return skill;
    }

    @Override
    public SkillConfig registerIfAbsent(SkillConfig skill) {

        if (skill.externalId() != null && idByExternalId.containsKey(skill.externalId()))
            return byId.get(idByExternalId.get(skill.externalId()));

        var existing = byId.putIfAbsent(skill.id(), skill);

        if (existing != null) return existing;

        idByName.putIfAbsent(skill.name(), skill.id());

        if (skill.externalId() != null)
            idByExternalId.putIfAbsent(skill.externalId(), skill.id());

        return skill;
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
}
