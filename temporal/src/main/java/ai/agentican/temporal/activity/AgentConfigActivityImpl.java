package ai.agentican.temporal.activity;

import ai.agentican.framework.config.AgentConfig;

public class AgentConfigActivityImpl implements AgentConfigActivity {

    private final AgentStepActivityImpl.AgentResolver agentResolver;

    public AgentConfigActivityImpl(AgentStepActivityImpl.AgentResolver agentResolver) {

        if (agentResolver == null) throw new IllegalArgumentException("agentResolver is required");

        this.agentResolver = agentResolver;
    }

    @Override
    public AgentConfig get(String agentRef) {

        var agent = agentResolver.resolve(agentRef);

        if (agent == null) throw new IllegalArgumentException("Unknown agent ref: " + agentRef);

        return agent.config();
    }
}
