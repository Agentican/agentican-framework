package ai.agentican.framework.hitl;

public record HitlCheckpoint(
        String id,
        HitlCheckpointType type,
        String stepName,
        String description,
        String content) {

    public HitlCheckpoint {

        if (id == null || id.isBlank())
            throw new IllegalArgumentException("Checkpoint ID is required");

        if (type == null)
            throw new IllegalArgumentException("Checkpoint type is required");
    }
}
