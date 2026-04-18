package ai.agentican.framework.orchestration.code;

/**
 * Registration metadata for a {@link CodeStep}. The {@code inputType} and
 * {@code outputType} drive Jackson's deserialization of plan-level
 * {@code PlanStepCode<I>.inputs()} and serialization of the executor's
 * return value into the stored step output.
 *
 * <p>Use {@link #of(String, Class, Class)} for the common case. Pass
 * {@code Void.class} for steps with no input or no meaningful output —
 * the framework substitutes {@code null} on input and an empty string on
 * output.
 */
public record CodeStepSpec<I, O>(
        String slug,
        String description,
        Class<I> inputType,
        Class<O> outputType) {

    @SuppressWarnings("unchecked")
    public CodeStepSpec {

        if (slug == null || slug.isBlank())
            throw new IllegalArgumentException("Code step slug is required");

        if (inputType == null)
            inputType = (Class<I>) (Class<?>) Void.class;

        if (outputType == null)
            outputType = (Class<O>) (Class<?>) Void.class;
    }

    public static <I, O> CodeStepSpec<I, O> of(String slug, Class<I> inputType, Class<O> outputType) {

        return new CodeStepSpec<>(slug, null, inputType, outputType);
    }

    public static <I, O> CodeStepSpec<I, O> of(String slug, String description,
                                                Class<I> inputType, Class<O> outputType) {

        return new CodeStepSpec<>(slug, description, inputType, outputType);
    }
}
