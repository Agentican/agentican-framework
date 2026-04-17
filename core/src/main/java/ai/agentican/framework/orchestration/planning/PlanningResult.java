package ai.agentican.framework.orchestration.planning;

import ai.agentican.framework.orchestration.model.Plan;

import java.util.Map;

public record PlanningResult(Plan plan, Map<String, String> inputs) {

    public PlanningResult {

        if (plan == null)
            throw new IllegalArgumentException("Plan is required");

        if (inputs == null)
            inputs = Map.of();
    }
}
