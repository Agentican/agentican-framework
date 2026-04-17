package ai.agentican.framework.llm;

import ai.agentican.framework.config.LlmConfig;

@FunctionalInterface
public interface LlmClientDecorator {

    LlmClient decorate(LlmConfig config, LlmClient client);
}
