package ai.agentican.framework.orchestration.model;

public record WorkflowParam(
        String name,
        String description,
        String defaultValue,
        boolean required) {

    public WorkflowParam {

        if (name == null || name.isBlank())
            throw new IllegalArgumentException("Parameter name is required");
    }
}
