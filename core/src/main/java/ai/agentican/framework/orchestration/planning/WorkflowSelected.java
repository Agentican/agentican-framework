package ai.agentican.framework.orchestration.planning;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record WorkflowSelected(
        String name,
        Map<String, String> inputs) implements WorkflowPlannerDecision {

    public WorkflowSelected {

        if (inputs == null)
            inputs = Map.of();
    }
}
