package ai.agentican.framework.orchestration.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import java.util.ArrayList;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonDeserialize(using = PlanStepCodeDeserializer.class)
public record PlanStepCode<I>(
        String name,
        String codeSlug,
        I input,
        List<String> dependencies) implements PlanStep {

    public PlanStepCode {

        if (name == null || name.isBlank())
            throw new IllegalArgumentException("Step name is required");

        if (codeSlug == null || codeSlug.isBlank())
            throw new IllegalArgumentException("Code step slug is required for step '" + name + "'");

        if (dependencies == null) dependencies = List.of();
    }

    @Override
    public boolean hitl() {

        return false;
    }

    public static <I> Builder<I> builder(String name) {

        return new Builder<>(name);
    }

    public static class Builder<I> {

        private final String name;
        private String codeSlug;
        private I input;
        private final List<String> dependencies = new ArrayList<>();

        Builder(String name) {

            this.name = name;
        }

        public Builder<I> code(String codeSlug) { this.codeSlug = codeSlug; return this; }
        public Builder<I> input(I input) { this.input = input; return this; }
        public Builder<I> dependency(String stepName) { this.dependencies.add(stepName); return this; }
        public Builder<I> dependencies(List<String> stepNames) { this.dependencies.addAll(stepNames); return this; }

        public PlanStepCode<I> build() {

            return new PlanStepCode<>(name, codeSlug, input, dependencies);
        }
    }
}
