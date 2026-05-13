package ai.agentican.temporal.dto;

import java.time.Duration;
import java.util.List;

public record AgentInvocationRequest(
        String agentRef,
        String renderedTask,
        String taskId,
        String stepId,
        String stepName,
        Duration timeout,
        List<String> skills,
        List<String> toolkitSlugs) {

    public AgentInvocationRequest {

        if (skills == null) skills = List.of();
        if (toolkitSlugs == null) toolkitSlugs = List.of();
    }
}
