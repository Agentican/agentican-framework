package ai.agentican.quarkus.rest.dto;

import ai.agentican.framework.orchestration.model.Plan;

import java.util.Map;

public record SubmitTaskRequest(
        String description,
        Plan task,
        String planId,
        Map<String, String> inputs) {

    public boolean isPlannerMode() {

        return description != null && !description.isBlank()
                && task == null && planId == null;
    }

    public boolean isPreBuiltMode() {

        return task != null
                && (description == null || description.isBlank())
                && (planId == null || planId.isBlank());
    }

    public boolean isPlanMode() {

        return planId != null && !planId.isBlank()
                && task == null
                && (description == null || description.isBlank());
    }
}
