package ai.agentican.temporal.dto;

import java.util.Map;

public record CodeInvocationRequest(
        String codeSlug,
        String taskId,
        String stepId,
        String stepName,
        Object input,
        Map<String, String> params,
        Map<String, String> stepOutputs) {

    public CodeInvocationRequest {

        if (params == null)      params = Map.of();
        if (stepOutputs == null) stepOutputs = Map.of();
    }
}
