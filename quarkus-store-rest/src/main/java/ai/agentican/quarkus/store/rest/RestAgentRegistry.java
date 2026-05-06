package ai.agentican.quarkus.store.rest;

import ai.agentican.framework.agent.Agent;
import ai.agentican.framework.config.AgentConfig;
import ai.agentican.framework.registry.AgentRegistry;

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
import java.util.function.Function;

@ApplicationScoped
@IfBuildProperty(name = "agentican.store.backend", stringValue = "rest")
public class RestAgentRegistry implements AgentRegistry {

    private static final Logger LOG = LoggerFactory.getLogger(RestAgentRegistry.class);

    private final ConcurrentMap<String, Agent> byId = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, String> idByName = new ConcurrentHashMap<>();

    private Function<AgentConfig, Agent> agentFactory;

    @Inject
    @RestClient
    RestCatalogClient client;

    @Override
    public void agentFactory(Function<AgentConfig, Agent> factory) {

        this.agentFactory = factory;
    }

    @Override
    public void seed() {

        if (agentFactory == null)
            throw new IllegalStateException(
                    "RestAgentRegistry has no factory set; call agentFactory(...) before seed()");

        try {

            var rows = client.listAgents();

            if (rows == null) return;

            for (var row : rows) {

                var cfg = new AgentConfig(row.id(), row.name(), row.role(), row.llm(), null, null, null);
                var agent = agentFactory.apply(cfg);

                byId.put(agent.id(), agent);
                idByName.put(agent.name(), agent.id());
            }

            if (!rows.isEmpty())
                LOG.info("RestAgentRegistry seeded {} agents from catalog", rows.size());
        }
        catch (Exception e) {

            throw new IllegalStateException(
                    "Failed to seed agents from REST catalog (check quarkus.rest-client.agentican-catalog.url): "
                            + e.getMessage(), e);
        }
    }

    @Override
    public Agent register(Agent agent) {

        byId.put(agent.id(), agent);
        idByName.put(agent.name(), agent.id());

        LOG.debug("Registered agent '{}' locally (not persisted to central catalog)", agent.name());
        return agent;
    }

    @Override
    public Agent register(AgentConfig config) {

        if (agentFactory == null)
            throw new IllegalStateException(
                    "RestAgentRegistry has no factory set; call agentFactory(...) before register(AgentConfig)");

        return register(agentFactory.apply(config));
    }

    @Override
    public Agent registerIfAbsent(Agent agent) {

        var existing = byId.get(agent.id());
        if (existing != null) return existing;
        return register(agent);
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
}
