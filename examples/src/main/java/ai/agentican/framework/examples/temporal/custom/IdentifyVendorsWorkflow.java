package ai.agentican.framework.examples.temporal.custom;

import ai.agentican.framework.examples.temporal.common.IdentifyVendorsInput;

import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

@WorkflowInterface
public interface IdentifyVendorsWorkflow {

    @WorkflowMethod
    String run(IdentifyVendorsInput input);
}
