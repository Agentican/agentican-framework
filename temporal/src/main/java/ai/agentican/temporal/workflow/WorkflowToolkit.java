package ai.agentican.temporal.workflow;

import ai.agentican.framework.hitl.HitlType;
import ai.agentican.framework.tools.Tool;
import ai.agentican.framework.tools.ToolDefinition;
import ai.agentican.framework.tools.Toolkit;

import java.util.List;
import java.util.Map;

public final class WorkflowToolkit implements Toolkit {

    private final String displayName;
    private final List<ToolDefinition> defs;
    private final Map<String, HitlType> hitlTypes;

    public WorkflowToolkit(String displayName, List<ToolDefinition> defs, Map<String, HitlType> hitlTypes) {

        this.displayName = displayName;
        this.defs = List.copyOf(defs);
        this.hitlTypes = hitlTypes == null ? Map.of() : Map.copyOf(hitlTypes);
    }

    public WorkflowToolkit(String displayName, List<ToolDefinition> defs) {

        this(displayName, defs, Map.of());
    }

    @Override public String displayName() { return displayName; }

    @Override
    public List<Tool> tools() {

        return List.of();
    }

    @Override
    public List<ToolDefinition> toolDefinitions() {

        return defs;
    }

    @Override
    public boolean handles(String toolName) {

        for (var d : defs) if (d.name().equals(toolName)) return true;

        return false;
    }

    @Override
    public HitlType hitlType(String toolName) {

        return hitlTypes.getOrDefault(toolName, HitlType.NONE);
    }

    @Override
    public String execute(String toolName, Map<String, Object> arguments) {

        throw new IllegalStateException(
                "WorkflowToolkit.execute should never be called — tool execution is routed via "
                        + "TemporalAgentLoopHost.executeTool → ToolCallActivity on the activity worker. "
                        + "Tool: " + toolName);
    }
}
