package ai.agentican.framework.event;

import ai.agentican.framework.orchestration.execution.resume.TurnResumeState;

public record TurnResumed(String taskId, String turnId, TurnResumeState state) implements AgenticanEvent { }
