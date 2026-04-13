package ai.agentican.framework.agent;

import ai.agentican.framework.state.RunLog;
import ai.agentican.framework.tools.ToolResult;
import ai.agentican.framework.tools.Toolkit;

import java.time.Duration;
import java.util.List;
import java.util.Map;

public interface AgentRunner {

    AgentResult run(Agent agent, String task, List<String> activeSkills, Map<String, Toolkit> toolkits, String taskId,
                    String stepId, String stepName);

    default AgentResult resume(Agent agent, String task, List<String> activeSkills, RunLog savedRun,
                               List<ToolResult> hitlToolResults, Map<String, Toolkit> toolkits, String taskId,
                               String stepId, String stepName) {

        throw new UnsupportedOperationException("This agent runner does not support HITL resume");
    }

    default AgentRunner withTimeout(Duration timeout) { return this; }
}
