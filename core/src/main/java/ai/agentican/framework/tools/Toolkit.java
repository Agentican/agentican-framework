package ai.agentican.framework.tools;

import ai.agentican.framework.hitl.HitlType;

import java.util.List;
import java.util.Map;

public interface Toolkit {

    default String displayName() { return null; }

    List<Tool> tools();

    boolean handles(String toolName);

    String execute(String toolName, Map<String, Object> arguments) throws Exception;

    default List<ToolDefinition> toolDefinitions() {

        return tools().stream().map(Tool::toDefinition).toList();
    }

    default HitlType hitlType(String toolName) {

        return tools().stream()
                .filter(t -> t.name().equals(toolName))
                .map(Tool::hitlType)
                .findFirst()
                .orElse(HitlType.NONE);
    }

    default boolean hitl(String toolName) {

        return hitlType(toolName) != HitlType.NONE;
    }
}
