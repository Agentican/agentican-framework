package ai.agentican.framework.agent;

import ai.agentican.framework.config.AgentConfig;

import java.util.Collection;
import java.util.Map;
import java.util.function.Function;

public interface AgentRegistry {

    void register(Agent agent);

    boolean isRegistered(String id);

    boolean isRegisteredByName(String name);

    Agent get(String id);

    Agent getByName(String name);

    Collection<Agent> getAll();

    Map<String, Agent> asMap();

    default void seed(Function<AgentConfig, Agent> factory) { }
}
