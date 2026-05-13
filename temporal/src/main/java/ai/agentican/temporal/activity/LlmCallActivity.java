package ai.agentican.temporal.activity;

import ai.agentican.framework.llm.LlmRequest;
import ai.agentican.framework.llm.LlmResponse;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

// fine-grained, within an agent step

@ActivityInterface
public interface LlmCallActivity {

    @ActivityMethod
    LlmResponse send(LlmRequest request);
}
