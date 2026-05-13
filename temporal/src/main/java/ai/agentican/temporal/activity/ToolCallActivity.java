package ai.agentican.temporal.activity;

import ai.agentican.temporal.dto.ToolCallRequest;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

// fine-grained, within an agent step

@ActivityInterface
public interface ToolCallActivity {

    @ActivityMethod
    String execute(ToolCallRequest request);
}
