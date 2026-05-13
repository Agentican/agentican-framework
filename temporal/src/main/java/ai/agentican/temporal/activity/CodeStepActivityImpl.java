package ai.agentican.temporal.activity;

import ai.agentican.framework.orchestration.code.CodeStep;
import ai.agentican.framework.orchestration.code.CodeStepContext;
import ai.agentican.temporal.dto.CodeInvocationRequest;
import ai.agentican.temporal.dto.CodeInvocationResult;

public class CodeStepActivityImpl implements CodeStepActivity {

    private final CodeStepResolver codeResolver;
    private final CodeStepContextProvider contextProvider;

    @FunctionalInterface
    public interface CodeStepContextProvider {

        CodeStepContext context(CodeInvocationRequest request);
    }

    public CodeStepActivityImpl(CodeStepResolver codeResolver, CodeStepContextProvider contextProvider) {

        if (codeResolver == null)    throw new IllegalArgumentException("codeResolver is required");
        if (contextProvider == null) throw new IllegalArgumentException("contextProvider is required");

        this.codeResolver = codeResolver;
        this.contextProvider = contextProvider;
    }

    @Override
    public CodeInvocationResult invokeCode(CodeInvocationRequest req) {

        var step = codeResolver.resolve(req.codeSlug());

        if (step == null)
            throw new IllegalArgumentException("Unknown code step slug: " + req.codeSlug());

        var ctx = contextProvider.context(req);
        var output = step.execute(req.input(), ctx);

        return new CodeInvocationResult(output);
    }

    @FunctionalInterface
    public interface CodeStepResolver {

        CodeStep<Object, Object> resolve(String slug);
    }
}
