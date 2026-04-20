package ai.agentican.framework.invoker;

import ai.agentican.framework.AgenticanRuntime;
import ai.agentican.framework.llm.StructuredOutput;
import ai.agentican.framework.orchestration.execution.OutputBinding;
import ai.agentican.framework.orchestration.execution.TaskHandle;
import ai.agentican.framework.orchestration.model.Plan;

import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * Single implementation of {@link Agentican} — the plan is obtained via
 * {@link #planSource}, which can either return a captured {@link Plan} reference
 * or resolve from the registry by name each call.
 */
record DefaultAgentican<P, R>(
        AgenticanRuntime runtime,
        Supplier<Plan> planSource,
        Class<P> paramsType,
        Class<R> outputType) implements Agentican<P, R> {

    DefaultAgentican {

        if (runtime == null)    throw new IllegalArgumentException("AgenticanRuntime is required");
        if (planSource == null) throw new IllegalArgumentException("planSource is required");
        if (paramsType == null) throw new IllegalArgumentException("paramsType is required");
        if (outputType == null) throw new IllegalArgumentException("outputType is required (use Void.class for none)");
    }

    @Override
    public TaskHandle run(P params) {

        var plan = planSource.get();
        var outputStep = AgenticanOutputReader.resolveOutputStep(plan, outputType);
        var binding = buildOutputBinding(outputStep, outputType);
        var inputs = AgenticanParamMapper.toStringMap(params);

        return runtime.run(plan, inputs, binding);
    }

    @Override
    public R runAndAwait(P params) {

        var plan = planSource.get();
        var outputStep = AgenticanOutputReader.resolveOutputStep(plan, outputType);
        var binding = buildOutputBinding(outputStep, outputType);
        var inputs = AgenticanParamMapper.toStringMap(params);

        return AgenticanOutputReader.readTypedOutput(
                runtime.run(plan, inputs, binding).result(), outputStep, outputType);
    }

    @Override
    public CompletableFuture<R> runAsync(P params) {

        var plan = planSource.get();
        var outputStep = AgenticanOutputReader.resolveOutputStep(plan, outputType);
        var binding = buildOutputBinding(outputStep, outputType);
        var inputs = AgenticanParamMapper.toStringMap(params);

        return runtime.run(plan, inputs, binding).resultAsync()
                .thenApply(r -> AgenticanOutputReader.readTypedOutput(r, outputStep, outputType));
    }

    private static OutputBinding buildOutputBinding(String stepName, Class<?> outputType) {

        if (stepName == null || outputType == null || outputType == Void.class) return null;
        if (outputType == String.class) return null;   // free-text expected

        var schema = SchemaGenerator.schemaFor(outputType);
        if (schema == null) return null;

        return new OutputBinding(stepName, new StructuredOutput(outputType.getSimpleName(), schema));
    }
}
