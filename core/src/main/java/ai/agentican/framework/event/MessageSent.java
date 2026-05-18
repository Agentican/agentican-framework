package ai.agentican.framework.event;

import ai.agentican.framework.llm.LlmRequest;

public record MessageSent(String taskId, String turnId, LlmRequest request) implements AgenticanEvent { }
