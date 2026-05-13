package ai.agentican.temporal.dto;

import ai.agentican.framework.hitl.HitlCheckpoint;

public record HitlCheckpointDto(
        String id,
        String type,
        String stepName,
        String description,
        String content) {

    public static HitlCheckpointDto from(HitlCheckpoint c) {

        if (c == null) return null;

        return new HitlCheckpointDto(c.id(), c.type().name(), c.stepName(), c.description(), c.content());
    }
}
