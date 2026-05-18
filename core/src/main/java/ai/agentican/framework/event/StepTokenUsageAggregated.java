package ai.agentican.framework.event;

import ai.agentican.framework.llm.TokenUsage;

public record StepTokenUsageAggregated(String taskId, String stepId, TokenUsage tokenUsage) implements AgenticanEvent { }
