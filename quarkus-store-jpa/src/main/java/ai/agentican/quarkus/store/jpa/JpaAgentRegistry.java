package ai.agentican.quarkus.store.jpa;

import ai.agentican.framework.agent.Agent;
import ai.agentican.framework.registry.AgentRegistry;
import ai.agentican.framework.config.AgentConfig;
import ai.agentican.quarkus.store.jpa.entity.AgentEntity;

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
import java.util.function.Function;

@ApplicationScoped
@IfBuildProperty(name = "agentican.store.backend", stringValue = "jpa", enableIfMissing = true)
public class JpaAgentRegistry implements AgentRegistry {

    private static final Logger LOG = LoggerFactory.getLogger(JpaAgentRegistry.class);

    private final ConcurrentMap<String, Agent> byId = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, String> idByName = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, String> idByExternalId = new ConcurrentHashMap<>();

    @Override
    @Transactional
    public void register(Agent agent) {

        var cfg = agent.config();

        if (cfg.externalId() == null) {
            LOG.debug("Agent '{}' has no externalId — skipping catalog persistence", agent.name());
            byId.put(agent.id(), agent);
            idByName.put(agent.name(), agent.id());
            return;
        }

        var existing = (AgentEntity) AgentEntity.find("externalId", cfg.externalId()).firstResult();
        AgentEntity e;
        Agent canonical = agent;

        if (existing != null) {
            e = existing;
            if (!e.id.equals(agent.id())) {
                var alignedCfg = new AgentConfig(e.id, cfg.name(), cfg.role(), cfg.llm(), cfg.externalId());
                canonical = new Agent(alignedCfg, agent.runner());
            }
        }
        else {
            e = new AgentEntity();
            e.id = agent.id();
            e.externalId = cfg.externalId();
            e.createdAt = Instant.now();
        }

        e.name = cfg.name();
        e.role = cfg.role();
        e.llm = cfg.llm();
        e.updatedAt = Instant.now();

        e.persist();

        byId.put(canonical.id(), canonical);
        idByName.put(canonical.name(), canonical.id());
        idByExternalId.put(cfg.externalId(), canonical.id());
    }

    @Override
    @Transactional
    public void seed(Function<AgentConfig, Agent> factory) {

        if (factory == null)
            throw new IllegalArgumentException("Agent factory is required for seed()");

        java.util.List<AgentEntity> rows = AgentEntity.listAll();

        for (var row : rows) {

            var cfg = new AgentConfig(row.id, row.name, row.role, row.llm, row.externalId);
            var agent = factory.apply(cfg);

            byId.put(agent.id(), agent);
            idByName.put(agent.name(), agent.id());
            if (row.externalId != null)
                idByExternalId.put(row.externalId, agent.id());
        }

        if (!rows.isEmpty())
            LOG.info("JpaAgentRegistry seeded {} agents from catalog", rows.size());
    }

    public Agent getByExternalId(String externalId) {

        var id = idByExternalId.get(externalId);
        return id != null ? byId.get(id) : null;
    }

    @Override
    public boolean isRegistered(String id) { return byId.containsKey(id); }

    @Override
    public boolean isRegisteredByName(String name) { return idByName.containsKey(name); }

    @Override
    public Agent get(String id) { return byId.get(id); }

    @Override
    public Agent getByName(String name) {

        var id = idByName.get(name);
        return id != null ? byId.get(id) : null;
    }

    @Override
    public Collection<Agent> getAll() { return Collections.unmodifiableCollection(byId.values()); }

    @Override
    public Map<String, Agent> asMap() { return Collections.unmodifiableMap(byId); }
}
