package ai.agentican.temporal.activity;

import ai.agentican.temporal.dto.CodeInvocationRequest;
import ai.agentican.temporal.dto.CodeInvocationResult;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

// course grained, single code step as an activity

@ActivityInterface
public interface CodeStepActivity {

    @ActivityMethod
    CodeInvocationResult invokeCode(CodeInvocationRequest request);
}
