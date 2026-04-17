package ai.agentican.framework.agent;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class InMemoryAgentRegistry implements AgentRegistry {

    private final ConcurrentMap<String, Agent> byId = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, String> idByName = new ConcurrentHashMap<>();

    @Override
    public void register(Agent agent) {

        byId.put(agent.id(), agent);
        idByName.put(agent.name(), agent.id());
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
    public Agent get(String id) {

        return byId.get(id);
    }

    @Override
    public Agent getByName(String name) {

        var id = idByName.get(name);
        return id != null ? byId.get(id) : null;
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
