package ai.agentican.framework.event;

import ai.agentican.framework.hitl.HitlResponse;

public record HitlResponded(String taskId, String stepId, String hitlId,
                            HitlResponse response) implements AgenticanEvent { }
