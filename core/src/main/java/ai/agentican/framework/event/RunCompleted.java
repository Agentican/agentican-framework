package ai.agentican.framework.event;

import ai.agentican.framework.agent.AgentStatus;
import ai.agentican.framework.llm.TokenUsage;

public record RunCompleted(String taskId, String stepId, String runId,
                           AgentStatus status, TokenUsage tokenUsage) implements AgenticanEvent { }
