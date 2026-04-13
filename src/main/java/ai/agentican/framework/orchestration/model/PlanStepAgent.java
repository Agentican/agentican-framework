package ai.agentican.framework.orchestration.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PlanStepAgent(
        String name,
        String agentName,
        String instructions,
        List<String> dependencies,
        boolean hitl,
        List<String> skills,
        List<String> toolkits,
        int maxRetries,
        Duration timeout,
        List<StepCondition> conditions,
        ConditionMode conditionMode) implements PlanStep {

    public PlanStepAgent {

        if (name == null || name.isBlank())
            throw new IllegalArgumentException("Step name is required");

        if (agentName == null || agentName.isBlank())
            throw new IllegalArgumentException("Agent name is required for step '" + name + "'");

        if (instructions == null || instructions.isBlank())
            throw new IllegalArgumentException("Instructions are required for step '" + name + "'");

        if (dependencies == null) dependencies = List.of();
        if (skills == null) skills = List.of();
        if (toolkits == null) toolkits = List.of();
        if (conditions == null) conditions = List.of();
        if (conditionMode == null) conditionMode = ConditionMode.ALL;
    }

    /** Backward-compatible constructor without maxRetries/timeout/conditions. */
    public PlanStepAgent(String name, String agentName, String instructions, List<String> dependencies,
                         boolean hitl, List<String> skills, List<String> toolkits) {

        this(name, agentName, instructions, dependencies, hitl, skills, toolkits, 0, null, null, null);
    }

    /** Backward-compatible constructor without conditions. */
    public PlanStepAgent(String name, String agentName, String instructions, List<String> dependencies,
                         boolean hitl, List<String> skills, List<String> toolkits,
                         int maxRetries, Duration timeout) {

        this(name, agentName, instructions, dependencies, hitl, skills, toolkits,
                maxRetries, timeout, null, null);
    }

    public static PlanStepAgent of(String name, String agentName, String instructions, List<String> dependencies,
                                   boolean hitl, List<String> skills, List<String> toolkits) {

        return new PlanStepAgent(name, agentName, instructions, dependencies, hitl, skills, toolkits);
    }

    public static Builder builder(String name) {

        return new Builder(name);
    }

    public static class Builder {

        private final String name;

        private final List<String> dependencies = new ArrayList<>();
        private final List<String> skills = new ArrayList<>();
        private final List<String> toolkits = new ArrayList<>();
        private final List<StepCondition> conditions = new ArrayList<>();

        private boolean hitl;
        private int maxRetries;
        private Duration timeout;
        private ConditionMode conditionMode;

        private String agentName;
        private String instructions;

        Builder(String name) {

            this.name = name;
        }

        public Builder agent(String agentName) { this.agentName = agentName; return this; }
        public Builder instructions(String instructions) { this.instructions = instructions; return this; }
        public Builder hitl(boolean hitl) { this.hitl = hitl; return this; }
        public Builder maxRetries(int maxRetries) { this.maxRetries = maxRetries; return this; }
        public Builder timeout(Duration timeout) { this.timeout = timeout; return this; }
        public Builder dependency(String stepName) { this.dependencies.add(stepName); return this; }
        public Builder dependencies(List<String> stepNames) { this.dependencies.addAll(stepNames); return this; }
        public Builder skill(String skillName) { this.skills.add(skillName); return this; }
        public Builder skills(List<String> skillNames) { this.skills.addAll(skillNames); return this; }
        public Builder toolkit(String toolkitSlug) { this.toolkits.add(toolkitSlug); return this; }
        public Builder toolkits(List<String> toolkitSlugs) { this.toolkits.addAll(toolkitSlugs); return this; }
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

            return new PlanStepAgent(name, agentName, instructions, dependencies, hitl, skills, toolkits,
                    maxRetries, timeout, conditions, conditionMode);
        }
    }
}
