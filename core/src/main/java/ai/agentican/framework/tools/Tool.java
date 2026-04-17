package ai.agentican.framework.tools;

import java.util.List;
import java.util.Map;

public interface Tool {

    String name();

    default String displayName() { return name(); }

    String description();

    Map<String, Object> properties();

    List<String> required();

    HitlType hitlType();

    default boolean hitl() {

        return hitlType() != HitlType.NONE;
    }

    default ToolDefinition toDefinition() {

        return new ToolDefinition(name(), description(), properties(), required());
    }
}
