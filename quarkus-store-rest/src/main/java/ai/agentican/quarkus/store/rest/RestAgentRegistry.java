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
    private final ConcurrentMap<String, String> idByExternalId = new ConcurrentHashMap<>();

    @Inject
    @RestClient
    RestCatalogClient client;

    @Override
    public void seed(Function<AgentConfig, Agent> factory) {

        if (factory == null)
            throw new IllegalArgumentException("Agent factory is required for seed()");

        try {

            var rows = client.listAgents();

            if (rows == null) return;

            for (var row : rows) {

                var cfg = new AgentConfig(row.id(), row.name(), row.role(), row.llm(), row.externalId());
                var agent = factory.apply(cfg);

                byId.put(agent.id(), agent);
                idByName.put(agent.name(), agent.id());

                if (row.externalId() != null)
                    idByExternalId.put(row.externalId(), agent.id());
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
    public void register(Agent agent) {

        byId.put(agent.id(), agent);
        idByName.put(agent.name(), agent.id());

        var externalId = agent.config().externalId();

        if (externalId != null)
            idByExternalId.put(externalId, agent.id());

        LOG.debug("Registered agent '{}' locally (not persisted to central catalog)", agent.name());
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
