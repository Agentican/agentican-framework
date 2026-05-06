package ai.agentican.framework.registry;

import ai.agentican.framework.agent.Agent;
import ai.agentican.framework.config.AgentConfig;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;

public class AgentRegistryMemory implements AgentRegistry {

    private final ConcurrentMap<String, Agent> byId = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Agent> byName = new ConcurrentHashMap<>();

    private Function<AgentConfig, Agent> agentFactory;

    @Override
    public void agentFactory(Function<AgentConfig, Agent> factory) {

        this.agentFactory = factory;
    }

    @Override
    public Agent register(Agent agent) {

        byId.put(agent.id(), agent);
        byName.put(agent.name(), agent);

        return agent;
    }

    @Override
    public Agent register(AgentConfig config) {

        if (agentFactory == null)
            throw new IllegalStateException(
                    "AgentRegistry has no factory set; call agentFactory(...) before register(AgentConfig)");

        return register(agentFactory.apply(config));
    }

    @Override
    public Agent registerIfAbsent(Agent agent) {

        var existing = byId.putIfAbsent(agent.id(), agent);

        if (existing != null) return existing;

        byName.putIfAbsent(agent.name(), agent);

        return agent;
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
    public Agent byId(String id) {

        return byId.get(id);
    }

    @Override
    public Agent byName(String name) {

        return byName.get(name);
    }

    @Override
    public Collection<Agent> list() {

        return Collections.unmodifiableCollection(byId.values());
    }

    @Override
    public Map<String, Agent> asMap() {

        return Collections.unmodifiableMap(byId);
    }

    @Override
    public void delete(String ref) {

        var agent = byId.get(ref);

        if (agent == null) agent = byName.get(ref);

        if (agent == null) return;

        byId.remove(agent.id());
        byName.remove(agent.name());
    }
}
