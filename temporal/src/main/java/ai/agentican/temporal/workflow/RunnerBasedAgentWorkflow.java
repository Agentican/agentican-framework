package ai.agentican.temporal.workflow;

import io.temporal.workflow.SignalMethod;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

@WorkflowInterface
public interface RunnerBasedAgentWorkflow {

    @WorkflowMethod
    String run(RunnerBasedAgentInput input);

    @SignalMethod
    void provideHitlResponse(String checkpointId, ai.agentican.framework.hitl.HitlResponse response);
}
