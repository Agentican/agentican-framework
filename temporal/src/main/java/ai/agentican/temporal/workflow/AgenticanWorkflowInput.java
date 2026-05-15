package ai.agentican.temporal.workflow;

import ai.agentican.framework.orchestration.model.WorkflowDefinition;

import java.util.Map;

public record AgenticanWorkflowInput(WorkflowDefinition plan, Map<String, String> params) {

    public AgenticanWorkflowInput {

        if (plan == null)   throw new IllegalArgumentException("plan is required");
        if (params == null) params = Map.of();
    }
}
