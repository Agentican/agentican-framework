package ai.agentican.quarkus.event;

import ai.agentican.framework.hitl.HitlCheckpoint;

public record HitlCheckpointEvent(String taskId, String stepId, String stepName, HitlCheckpoint checkpoint) {}
