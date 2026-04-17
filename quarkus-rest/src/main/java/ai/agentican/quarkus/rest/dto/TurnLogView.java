package ai.agentican.quarkus.rest.dto;

import ai.agentican.framework.state.TurnLog;

import java.time.Instant;

public record TurnLogView(
        String id,
        int index,
        Instant startedAt,
        Instant completedAt,
        String messageId,
        String responseId,
        String stopReason,
        String state,
        String resumeState) {

    public static TurnLogView of(TurnLog turn) {

        var response = turn.response();

        return new TurnLogView(
                turn.id(),
                turn.index(),
                turn.startedAt(),
                turn.completedAt(),
                turn.messageId(),
                turn.responseId(),
                response != null && response.stopReason() != null ? response.stopReason().name() : null,
                turn.state() != null ? turn.state().name() : null,
                turn.resumeState() != null ? turn.resumeState().name() : null);
    }
}
