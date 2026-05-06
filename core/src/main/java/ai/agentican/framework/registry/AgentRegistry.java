package ai.agentican.framework.registry;

import ai.agentican.framework.agent.Agent;
import ai.agentican.framework.config.AgentConfig;

import java.util.function.Function;

public interface AgentRegistry extends Catalog<Agent> {

    Agent register(AgentConfig config);

    void agentFactory(Function<AgentConfig, Agent> factory);

    default void seed() { }
}
