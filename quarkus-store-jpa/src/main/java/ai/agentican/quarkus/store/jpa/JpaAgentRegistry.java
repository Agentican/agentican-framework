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

    private Function<AgentConfig, Agent> agentFactory;

    @Override
    public void agentFactory(Function<AgentConfig, Agent> factory) {

        this.agentFactory = factory;
    }

    @Override
    @Transactional
    public Agent register(Agent agent) {

        var cfg = agent.config();

        var existing = (AgentEntity) AgentEntity.findById(agent.id());
        var e = existing != null ? existing : new AgentEntity();

        if (existing == null) {
            e.id = agent.id();
            e.createdAt = Instant.now();
        }

        e.name = cfg.name();
        e.role = cfg.role();
        e.llm = cfg.llm();
        e.updatedAt = Instant.now();

        e.persist();

        byId.put(agent.id(), agent);
        idByName.put(agent.name(), agent.id());

        return agent;
    }

    @Override
    @Transactional
    public Agent register(AgentConfig config) {

        if (agentFactory == null)
            throw new IllegalStateException(
                    "JpaAgentRegistry has no factory set; call agentFactory(...) before register(AgentConfig)");

        return register(agentFactory.apply(config));
    }

    @Override
    @Transactional
    public Agent registerIfAbsent(Agent agent) {

        var existing = byId.get(agent.id());
        if (existing != null) return existing;
        return register(agent);
    }

    @Override
    @Transactional
    public void seed() {

        if (agentFactory == null)
            throw new IllegalStateException(
                    "JpaAgentRegistry has no factory set; call agentFactory(...) before seed()");

        java.util.List<AgentEntity> rows = AgentEntity.listAll();

        for (var row : rows) {

            var cfg = new AgentConfig(row.id, row.name, row.role, row.llm, null, null, null);
            var agent = agentFactory.apply(cfg);

            byId.put(agent.id(), agent);
            idByName.put(agent.name(), agent.id());
        }

        if (!rows.isEmpty())
            LOG.info("JpaAgentRegistry seeded {} agents from catalog", rows.size());
    }

    @Override
    public boolean hasById(String id) { return byId.containsKey(id); }

    @Override
    public boolean hasByName(String name) { return idByName.containsKey(name); }

    @Override
    public Agent byId(String id) { return byId.get(id); }

    @Override
    public Agent byName(String name) {

        var id = idByName.get(name);
        return id != null ? byId.get(id) : null;
    }

    @Override
    public Collection<Agent> list() { return Collections.unmodifiableCollection(byId.values()); }

    @Override
    public Map<String, Agent> asMap() { return Collections.unmodifiableMap(byId); }

    @Override
    @Transactional
    public void delete(String ref) {

        var agent = resolve(ref);

        if (agent == null) {
            LOG.debug("delete('{}'): no agent registered under this ref", ref);
            return;
        }

        AgentEntity.deleteById(agent.id());

        byId.remove(agent.id());
        idByName.remove(agent.name());

        LOG.info("Agent '{}' (id={}) deleted from catalog", agent.name(), agent.id());
    }

    private Agent resolve(String ref) {

        var byIdHit = byId.get(ref);
        if (byIdHit != null) return byIdHit;

        return byName(ref);
    }
}
