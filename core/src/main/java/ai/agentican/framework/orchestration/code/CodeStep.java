package ai.agentican.framework.orchestration.code;

/**
 * A deterministic step in a {@link ai.agentican.framework.orchestration.model.Plan}
 * that runs plain Java instead of going through an LLM. Registered with the
 * {@code AgenticanRuntime} builder via {@link CodeStepSpec} and referenced from
 * {@code PlanStepCode<I>}.
 *
 * <p>{@code I} is the typed input record (or {@code Void} / {@code Map} /
 * {@code JsonNode} for special cases — see {@link CodeStepSpec}). {@code O}
 * is the typed output, which the framework JSON-serializes for storage so
 * downstream steps can read fields via {@code {{step.X.output.field}}}.
 *
 * <p>Lambdas and classes both satisfy this interface — quick one-liners for
 * glue logic, classes with constructor-injected dependencies for real work.
 */
@FunctionalInterface
public interface CodeStep<I, O> {

    O execute(I input, StepContext context);
}
