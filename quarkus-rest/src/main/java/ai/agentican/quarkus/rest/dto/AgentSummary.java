package ai.agentican.quarkus.rest.dto;

import ai.agentican.framework.agent.Agent;

public record AgentSummary(

        String id,
        String name,
        String role,
        String llm,
        String externalId,
        boolean declaredInConfig) {

    public static AgentSummary of(Agent agent, boolean declaredInConfig) {

        return new AgentSummary(
                agent.id(),
                agent.name(),
                agent.role(),
                agent.config().llm(),
                agent.config().externalId(),
                declaredInConfig);
    }
}
