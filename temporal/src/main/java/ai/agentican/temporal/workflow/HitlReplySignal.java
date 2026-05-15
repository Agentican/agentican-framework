package ai.agentican.temporal.workflow;

import ai.agentican.framework.tools.ToolResult;

import java.util.List;

public record HitlReplySignal(String stepName, List<ToolResult> toolResults) {

    public HitlReplySignal {

        if (stepName == null || stepName.isBlank())
            throw new IllegalArgumentException("stepName is required");

        if (toolResults == null) toolResults = List.of();
    }
}
