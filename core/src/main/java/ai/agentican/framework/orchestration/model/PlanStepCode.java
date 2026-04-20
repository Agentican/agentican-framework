package ai.agentican.framework.orchestration.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import java.util.ArrayList;
import java.util.List;

/**
 * A step in a {@link Plan} that runs a registered deterministic function
 * (no LLM round-trip). References a registered {@code CodeStepSpec} by
 * {@link #codeSlug()}; the runtime executor and {@code Class<I>} are
 * resolved via the framework's {@code CodeStepRegistry} at dispatch time.
 *
 * <p>Code steps have no turn-level granularity and always have
 * {@link #hitl()} of {@code false}. On crash recovery, interrupted code
 * steps re-run from scratch — developers are responsible for making their
 * functions idempotent or fast.
 *
 * <p>Deserialization is registry-aware: the custom
 * {@link PlanStepCodeDeserializer} consults a {@code CodeStepRegistry}
 * injected via Jackson's {@code InjectableValues} to resolve the slug to a
 * {@code Class<I>} and produce a typed inputs value.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonDeserialize(using = PlanStepCodeDeserializer.class)
public record PlanStepCode<I>(
        String name,
        String codeSlug,
        I inputs,
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
        private I inputs;
        private final List<String> dependencies = new ArrayList<>();

        Builder(String name) {

            this.name = name;
        }

        public Builder<I> code(String codeSlug) { this.codeSlug = codeSlug; return this; }
        public Builder<I> inputs(I inputs) { this.inputs = inputs; return this; }
        public Builder<I> dependency(String stepName) { this.dependencies.add(stepName); return this; }
        public Builder<I> dependencies(List<String> stepNames) { this.dependencies.addAll(stepNames); return this; }

        public PlanStepCode<I> build() {

            return new PlanStepCode<>(name, codeSlug, inputs, dependencies);
        }
    }
}
