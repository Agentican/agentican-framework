package ai.agentican.framework.orchestration.execution.resume;

public enum TurnResumeState {

    NONE,
    STARTED_NO_MESSAGE,
    MESSAGE_SENT,
    RESPONSE_RECEIVED,
    TOOLS_PARTIAL,
    TOOLS_COMPLETE,
    CLOSED
}
