package ai.agentican.framework.event;

import ai.agentican.framework.llm.TokenUsage;

public record TurnCompleted(String taskId, String turnId, int index, TokenUsage tokenUsage) implements AgenticanEvent { }
