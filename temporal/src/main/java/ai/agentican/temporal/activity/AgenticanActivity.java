package ai.agentican.temporal.activity;

import ai.agentican.framework.event.AgenticanEvent;
import ai.agentican.framework.state.WorkflowRunLog;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

@ActivityInterface
public interface AgenticanActivity {

    @ActivityMethod
    void publish(AgenticanEvent event);

    @ActivityMethod
    WorkflowRunLog loadRunLog(String taskId);
}
