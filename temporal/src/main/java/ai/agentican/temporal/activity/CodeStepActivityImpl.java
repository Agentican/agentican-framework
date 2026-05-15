package ai.agentican.temporal.activity;

import ai.agentican.framework.orchestration.code.CodeStep;
import ai.agentican.framework.orchestration.code.CodeStepContext;
import ai.agentican.framework.orchestration.code.CodeStepRegistry;
import ai.agentican.framework.orchestration.execution.OrchestrationHelpers;
import ai.agentican.temporal.dto.CodeInvocationRequest;
import ai.agentican.temporal.dto.CodeInvocationResult;

public class CodeStepActivityImpl implements CodeStepActivity {

    private final CodeStepRegistry registry;
    private final CodeStepContextProvider contextProvider;

    public CodeStepActivityImpl(CodeStepRegistry registry, CodeStepContextProvider contextProvider) {

        if (registry == null) throw new IllegalArgumentException("CodeStepRegistry is required");
        if (contextProvider == null) throw new IllegalArgumentException("contextProvider is required");

        this.registry = registry;
        this.contextProvider = contextProvider;
    }

    @Override
    public CodeInvocationResult invokeCode(CodeInvocationRequest req) {

        var entry = registry.get(req.codeSlug());

        if (entry == null)
            throw new IllegalArgumentException("Unknown code step slug: " + req.codeSlug());

        var spec = entry.spec();

        var typedInput = OrchestrationHelpers.resolveInput(req.input(), spec.inputType(),
                req.params(), req.stepOutputs());

        var ctx = contextProvider.context(req);

        @SuppressWarnings({"unchecked", "rawtypes"})
        var output = ((CodeStep) entry.executor()).execute(typedInput, ctx);

        var stored = OrchestrationHelpers.serializeOutput(output, spec.outputType());

        return new CodeInvocationResult(stored);
    }

    @FunctionalInterface
    public interface CodeStepContextProvider {

        CodeStepContext context(CodeInvocationRequest request);
    }
}
