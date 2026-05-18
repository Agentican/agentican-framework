package ai.agentican.framework.event;

import ai.agentican.framework.hitl.HitlCheckpoint;

public record HitlNotified(String taskId, String stepId, HitlCheckpoint checkpoint) implements AgenticanEvent { }
