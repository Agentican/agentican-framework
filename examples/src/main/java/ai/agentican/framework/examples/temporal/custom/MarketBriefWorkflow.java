package ai.agentican.framework.examples.temporal.custom;

import ai.agentican.framework.examples.temporal.common.MarketBriefParams;
import ai.agentican.framework.tools.ToolResult;

import io.temporal.workflow.SignalMethod;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

import java.util.List;

@WorkflowInterface
public interface MarketBriefWorkflow {

    @WorkflowMethod
    String run(MarketBriefParams params);

    @SignalMethod
    void provideHitlReply(List<ToolResult> toolResults);
}
