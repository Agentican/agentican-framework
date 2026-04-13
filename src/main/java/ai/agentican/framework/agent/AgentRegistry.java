package ai.agentican.framework.agent;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class AgentRegistry {

    private final ConcurrentMap<String, Agent> agents = new ConcurrentHashMap<>();

    public void register(Agent agent) {

        agents.put(agent.name(), agent);
    }

    public boolean isRegistered(String name) {

        return agents.containsKey(name);
    }

    public Agent get(String name) {

        return agents.get(name);
    }

    public Collection<Agent> getAll() {

        return Collections.unmodifiableCollection(agents.values());
    }

    public Map<String, Agent> asMap() {

        return Collections.unmodifiableMap(agents);
    }
}
