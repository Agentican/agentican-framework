package ai.agentican.framework.orchestration.model;

public record PlanParam(
        String name,
        String description,
        String defaultValue,
        boolean required) {

    public PlanParam {

        if (name == null || name.isBlank())
            throw new IllegalArgumentException("Parameter name is required");
    }
}
