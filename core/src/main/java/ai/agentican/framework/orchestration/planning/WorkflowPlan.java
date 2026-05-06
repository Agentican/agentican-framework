package ai.agentican.framework.orchestration.planning;

import ai.agentican.framework.orchestration.model.WorkflowDefinition;

import java.util.Map;

public record WorkflowPlan(WorkflowDefinition definition, Map<String, String> inputs) {

    public WorkflowPlan {

        if (definition == null)
            throw new IllegalArgumentException("WorkflowDefinition is required");

        if (inputs == null)
            inputs = Map.of();
    }
}
