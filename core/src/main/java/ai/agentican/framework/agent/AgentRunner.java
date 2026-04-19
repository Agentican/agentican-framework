package ai.agentican.framework.agent;

import ai.agentican.framework.orchestration.execution.resume.ResumePlan;
import ai.agentican.framework.state.RunLog;
import ai.agentican.framework.tools.ToolResult;
import ai.agentican.framework.tools.Toolkit;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public interface AgentRunner {


    AgentResult run(Agent agent, String task, List<String> activeSkills, Map<String, Toolkit> toolkits,
                    String taskId, String stepId, String stepName, Duration timeoutOverride);

    default AgentResult resume(Agent agent, String task, List<String> activeSkills, RunLog savedRun,
                               List<ToolResult> hitlToolResults, Map<String, Toolkit> toolkits,
                               String taskId, String stepId, String stepName, Duration timeoutOverride) {

        throw new UnsupportedOperationException("This agent runner does not support HITL resume");
    }

    default AgentResult resumeAfterCrash(Agent agent, String task, List<String> activeSkills,
                                         RunLog savedRun, ResumePlan resumePlan,
                                         Map<String, Toolkit> toolkits,
                                         String taskId, String stepId, String stepName,
                                         AtomicBoolean cancelled) {

        return AgentResult.builder().status(AgentStatus.FAILED).run(savedRun).build();
    }
}
