package ai.agentican.framework.orchestration.code;

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

}
