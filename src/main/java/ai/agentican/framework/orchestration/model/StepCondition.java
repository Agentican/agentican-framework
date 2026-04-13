package ai.agentican.framework.orchestration.model;

public record StepCondition(String source, ConditionOp op, String value) {

    public StepCondition {

        if (source == null || source.isBlank())
            throw new IllegalArgumentException("Condition source is required");

        if (op == null)
            throw new IllegalArgumentException("Condition op is required");
    }

    public StepCondition(String source, ConditionOp op) {

        this(source, op, null);
    }
}
