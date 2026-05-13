package ai.agentican.temporal.dto;

import ai.agentican.framework.agent.AgentStatus;

public record AgentInvocationResult(
        AgentStatus status,
        String text,
        HitlCheckpointDto hitlCheckpoint,
        TokenUsageDto tokens) {

}
