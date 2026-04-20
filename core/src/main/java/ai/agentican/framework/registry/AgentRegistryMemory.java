package ai.agentican.framework.registry;

import ai.agentican.framework.agent.Agent;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class AgentRegistryMemory implements AgentRegistry {

    private final ConcurrentMap<String, Agent> byId = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Agent> byName = new ConcurrentHashMap<>();

    @Override
    public void register(Agent agent) {

        byId.put(agent.id(), agent);
        byName.put(agent.name(), agent);
    }

    @Override
    public boolean isRegistered(String id) {

        return byId.containsKey(id);
    }

    @Override
    public boolean isRegisteredByName(String name) {

        return byName.containsKey(name);
    }

    @Override
    public Agent get(String id) {

        return byId.get(id);
    }

    @Override
    public Agent getByName(String name) {

        return byName.get(name);
    }

    @Override
    public Collection<Agent> getAll() {

        return Collections.unmodifiableCollection(byId.values());
    }

    @Override
    public Map<String, Agent> asMap() {

        return Collections.unmodifiableMap(byId);
    }
}
