package ai.agentican.framework.orchestration.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PlanStepAgent(
        String name,
        String agentId,
        String instructions,
        List<String> dependencies,
        boolean hitl,
        List<String> skills,
        List<String> tools,
        int maxRetries,
        Duration timeout,
        List<StepCondition> conditions,
        ConditionMode conditionMode) implements PlanStep {

    public PlanStepAgent {

        if (name == null || name.isBlank())
            throw new IllegalArgumentException("Step name is required");

        if (agentId == null || agentId.isBlank())
            throw new IllegalArgumentException("Agent id is required for step '" + name + "'");

        if (instructions == null || instructions.isBlank())
            throw new IllegalArgumentException("Instructions are required for step '" + name + "'");

        if (dependencies == null) dependencies = List.of();
        if (skills == null) skills = List.of();
        if (tools == null) tools = List.of();
        if (conditions == null) conditions = List.of();
        if (conditionMode == null) conditionMode = ConditionMode.ALL;
    }

    public PlanStepAgent(String name, String agentId, String instructions, List<String> dependencies,
                         boolean hitl, List<String> skills, List<String> tools) {

        this(name, agentId, instructions, dependencies, hitl, skills, tools, 0, null, null, null);
    }

    public PlanStepAgent(String name, String agentId, String instructions, List<String> dependencies,
                         boolean hitl, List<String> skills, List<String> tools,
                         int maxRetries, Duration timeout) {

        this(name, agentId, instructions, dependencies, hitl, skills, tools,
                maxRetries, timeout, null, null);
    }

    public static Builder builder(String name) {

        return new Builder(name);
    }

    public static class Builder {

        private final String name;

        private final List<String> dependencies = new ArrayList<>();
        private final List<String> skills = new ArrayList<>();
        private final List<String> tools = new ArrayList<>();
        private final List<StepCondition> conditions = new ArrayList<>();

        private boolean hitl;
        private int maxRetries;
        private Duration timeout;
        private ConditionMode conditionMode;

        private String agentId;
        private String instructions;

        Builder(String name) {

            this.name = name;
        }

        public Builder agent(String agentId) { this.agentId = agentId; return this; }
        public Builder instructions(String instructions) { this.instructions = instructions; return this; }
        public Builder hitl(boolean hitl) { this.hitl = hitl; return this; }
        public Builder maxRetries(int maxRetries) { this.maxRetries = maxRetries; return this; }
        public Builder timeout(Duration timeout) { this.timeout = timeout; return this; }
        public Builder dependency(String stepName) { this.dependencies.add(stepName); return this; }
        public Builder dependencies(List<String> stepNames) { this.dependencies.addAll(stepNames); return this; }
        public Builder skill(String skillId) { this.skills.add(skillId); return this; }
        public Builder skills(List<String> skillIds) { this.skills.addAll(skillIds); return this; }
        public Builder tool(String toolName) { this.tools.add(toolName); return this; }
        public Builder tools(List<String> toolNames) { this.tools.addAll(toolNames); return this; }
        public Builder conditionMode(ConditionMode mode) { this.conditionMode = mode; return this; }

        public Builder condition(String source, ConditionOp op, String value) {

            this.conditions.add(new StepCondition(source, op, value));
            return this;
        }

        public Builder condition(String source, ConditionOp op) {

            this.conditions.add(new StepCondition(source, op));
            return this;
        }

        public PlanStepAgent build() {

            return new PlanStepAgent(name, agentId, instructions, dependencies, hitl, skills, tools,
                    maxRetries, timeout, conditions, conditionMode);
        }
    }
}
