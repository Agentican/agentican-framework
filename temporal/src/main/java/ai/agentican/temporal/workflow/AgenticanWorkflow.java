package ai.agentican.temporal.workflow;

import io.temporal.workflow.SignalMethod;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

@WorkflowInterface
public interface AgenticanWorkflow {

    @WorkflowMethod
    String run(AgenticanWorkflowInput input);

    @SignalMethod
    void provideHitlReply(HitlReplySignal reply);
}
