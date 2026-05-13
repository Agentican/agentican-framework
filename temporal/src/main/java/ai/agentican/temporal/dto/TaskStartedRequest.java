package ai.agentican.temporal.dto;

import ai.agentican.framework.orchestration.model.WorkflowDefinition;

import java.util.Map;

public record TaskStartedRequest(
        String taskId,
        String taskName,
        WorkflowDefinition plan,
        Map<String, String> params,
        String parentTaskId,
        String parentStepId,
        int iterationIndex) {

    public TaskStartedRequest {

        if (params == null) params = Map.of();
    }

    public TaskStartedRequest(String taskId, String taskName, WorkflowDefinition plan, Map<String, String> params) {

        this(taskId, taskName, plan, params, null, null, 0);
    }
}
