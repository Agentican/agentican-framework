package ai.agentican.framework.hitl;

public record HitlCheckpoint(
        String id,
        Type type,
        String stepName,
        String description,
        String content) {

    public HitlCheckpoint {

        if (id == null || id.isBlank())
            throw new IllegalArgumentException("Checkpoint ID is required");

        if (type == null)
            throw new IllegalArgumentException("Checkpoint type is required");
    }

    public enum Type {

        TOOL_CALL,
        STEP_OUTPUT,
        QUESTION
    }
}
