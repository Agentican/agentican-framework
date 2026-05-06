package ai.agentican.framework;

import ai.agentican.framework.llm.StructuredOutput;
import ai.agentican.framework.orchestration.execution.OutputBinding;
import ai.agentican.framework.orchestration.execution.WorkflowRun;
import ai.agentican.framework.orchestration.model.WorkflowDefinition;

public final class Workflow<P, R> {

    private final WorkflowEngine engine;
    private final WorkflowDefinition definition;
    private final Class<R> outputType;
    private final String outputStep;
    private final OutputBinding binding;

    Workflow(WorkflowEngine engine, WorkflowDefinition definition, Class<R> outputType) {

        if (engine == null) throw new IllegalArgumentException("WorkflowEngine is required");
        if (definition == null) throw new IllegalArgumentException("WorkflowDefinition is required");
        if (outputType == null) throw new IllegalArgumentException("outputType is required (use Void.class for none)");

        this.engine = engine;
        this.definition = definition;
        this.outputType = outputType;
        this.outputStep = WorkflowOutputMapper.resolveOutputStep(definition, outputType);
        this.binding = buildOutputBinding(outputStep, outputType);
    }

    public WorkflowRun<R> start() {

        return start(null);
    }

    public WorkflowRun<R> start(P params) {

        var inputs = WorkflowInputMapper.toStringMap(params);

        return engine.dispatch(definition, inputs, binding, result ->
                WorkflowOutputMapper.readTypedOutput(result, outputStep, outputType));
    }

    private static OutputBinding buildOutputBinding(String stepName, Class<?> outputType) {

        if (stepName == null || outputType == null || outputType == Void.class) return null;

        if (outputType == String.class) return null;

        var schema = WorkflowSchemaGenerator.schemaFor(outputType);

        if (schema == null) return null;

        return new OutputBinding(stepName, new StructuredOutput(outputType.getSimpleName(), schema));
    }
}
