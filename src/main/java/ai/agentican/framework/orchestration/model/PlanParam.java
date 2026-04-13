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

    public PlanParam(String name) {

        this(name, null, null, true);
    }

    public PlanParam(String name, String description) {

        this(name, description, null, true);
    }

    public PlanParam(String name, String description, String defaultValue) {

        this(name, description, defaultValue, false);
    }

    public static PlanParam of(String name, String description, String defaultValue, boolean required) {

        return new PlanParam(name, description, defaultValue, required);
    }
}
