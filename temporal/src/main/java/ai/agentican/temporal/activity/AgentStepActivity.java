package ai.agentican.temporal.activity;

import ai.agentican.temporal.dto.AgentInvocationRequest;
import ai.agentican.temporal.dto.AgentInvocationResult;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

// course grained, single agent step as an activity
// simple, more control, but more expensive in terms of recovery (repeat llm/tool calls)

@ActivityInterface
public interface AgentStepActivity {

    @ActivityMethod
    AgentInvocationResult invokeAgent(AgentInvocationRequest request);
}
