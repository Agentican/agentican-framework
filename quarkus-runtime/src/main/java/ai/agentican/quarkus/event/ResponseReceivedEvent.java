package ai.agentican.quarkus.event;

import ai.agentican.framework.llm.StopReason;

public record ResponseReceivedEvent(String responseId, String turnId, String agentName, int turn,
                                    StopReason stopReason, long inputTokens, long outputTokens,
                                    int toolCallCount, String taskId) {}
