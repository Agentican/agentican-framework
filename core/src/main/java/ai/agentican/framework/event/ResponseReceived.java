package ai.agentican.framework.event;

import ai.agentican.framework.llm.LlmResponse;

public record ResponseReceived(String taskId, String turnId, LlmResponse response) implements AgenticanEvent { }
