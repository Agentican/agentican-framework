package ai.agentican.framework.agent;

import ai.agentican.framework.llm.StructuredOutput;
import ai.agentican.framework.orchestration.execution.resume.ResumePlan;
import ai.agentican.framework.state.RunLog;
import ai.agentican.framework.tools.ToolResult;
import ai.agentican.framework.tools.Toolkit;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public interface AgentRunner {


    AgentResult run(Agent agent, String task, String taskId, String stepId, String stepName, Duration timeout,
                    List<String> skills, Map<String, Toolkit> toolkits, StructuredOutput outputSchema);

    default AgentResult resume(Agent agent, String task, String taskId, String stepId, String stepName, Duration timeout,
                               List<String> skills, Map<String, Toolkit> toolkits, StructuredOutput outputSchema,
                               RunLog savedRun, List<ToolResult> hitlToolResults) {

        throw new UnsupportedOperationException("This agent runner does not support HITL resume");
    }

    default AgentResult resumeAfterCrash(Agent agent, String task, String taskId, String stepId, String stepName,
                                         List<String> skills, Map<String, Toolkit> toolkits, StructuredOutput outputSchema,
                                         RunLog savedRun, AtomicBoolean cancelled, ResumePlan resumePlan) {

        return AgentResult.builder().status(AgentStatus.FAILED).run(savedRun).build();
    }
}
