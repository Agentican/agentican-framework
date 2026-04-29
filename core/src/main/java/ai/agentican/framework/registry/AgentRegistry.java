package ai.agentican.framework.registry;

import ai.agentican.framework.agent.Agent;
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

    default Agent getByExternalId(String externalId) {

        if (externalId == null) return null;
        for (var agent : getAll())
            if (externalId.equals(agent.config().externalId())) return agent;
        return null;
    }

    Collection<Agent> getAll();

    Map<String, Agent> asMap();

    default void seed(Function<AgentConfig, Agent> factory) { }

    default void delete(String ref) {

        throw new UnsupportedOperationException(
                getClass().getSimpleName() + " is read-only; delete not supported");
    }
}
