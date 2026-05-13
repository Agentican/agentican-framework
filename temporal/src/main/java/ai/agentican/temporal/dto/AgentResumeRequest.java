package ai.agentican.temporal.dto;

import ai.agentican.framework.tools.ToolResult;

import java.util.List;

public record AgentResumeRequest(
        AgentInvocationRequest originalRequest,
        String runId,
        List<ToolResult> hitlToolResults) {

    public AgentResumeRequest {

        if (originalRequest == null) throw new IllegalArgumentException("originalRequest is required");
        if (runId == null || runId.isBlank()) throw new IllegalArgumentException("runId is required");
        if (hitlToolResults == null) hitlToolResults = List.of();
    }
}
