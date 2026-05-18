package ai.agentican.temporal.activity;

import ai.agentican.framework.config.AgentConfig;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

@ActivityInterface
public interface AgentConfigActivity {

    @ActivityMethod
    AgentConfig get(String agentRef);
}
