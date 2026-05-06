package ai.agentican.framework;

import ai.agentican.framework.orchestration.model.WorkflowDefinition;
import ai.agentican.framework.orchestration.model.WorkflowParam;
import ai.agentican.framework.orchestration.model.WorkflowStep;

import java.util.ArrayList;
import java.util.List;

public final class WorkflowBuilder {

    private final WorkflowEngine engine;
    private final String workflowName;
    private final WorkflowDefinition predefined;

    private final List<WorkflowStep> inlineSteps = new ArrayList<>();
    private final List<WorkflowParam> inlineParams = new ArrayList<>();

    private String inlineDescription;
    private String inlineOutputStep;

    public WorkflowBuilder(WorkflowEngine engine, String workflowName) {

        this(engine, workflowName, null);
    }

    public WorkflowBuilder(WorkflowEngine engine, WorkflowDefinition definition) {

        this(engine, definition != null ? definition.name() : null, definition);

        if (definition == null)
            throw new IllegalArgumentException("WorkflowDefinition is required");
    }

    private WorkflowBuilder(WorkflowEngine engine, String workflowName, WorkflowDefinition predefined) {

        if (engine == null)
            throw new IllegalArgumentException("WorkflowEngine is required");
        if (workflowName == null || workflowName.isBlank())
            throw new IllegalArgumentException("Workflow name is required");

        this.engine = engine;
        this.workflowName = workflowName;
        this.predefined = predefined;
    }

    /** Inline DSL: set the workflow description. Cannot be used with a predefined definition. */
    public WorkflowBuilder description(String description) {

        checkInline();
        this.inlineDescription = description;
        return this;
    }

    /** Inline DSL: declare a parameter (required, no default). */
    public WorkflowBuilder param(String name) {

        checkInline();
        inlineParams.add(new WorkflowParam(name, null, null, true));
        return this;
    }

    /** Inline DSL: declare a parameter (required, with description). */
    public WorkflowBuilder param(String name, String description) {

        checkInline();
        inlineParams.add(new WorkflowParam(name, description, null, true));
        return this;
    }

    /** Inline DSL: declare a parameter with description and default value (optional). */
    public WorkflowBuilder param(String name, String description, String defaultValue) {

        checkInline();
        inlineParams.add(new WorkflowParam(name, description, defaultValue, false));
        return this;
    }

    /** Inline DSL: append a pre-built step. */
    public WorkflowBuilder step(WorkflowStep step) {

        checkInline();
        inlineSteps.add(step);
        return this;
    }

    /** Inline DSL: append an agent step (shortcut for {@code .step(WorkflowStepAgent.builder(...).build())}). */
    public WorkflowBuilder step(String stepName, String agentName, String instructions) {

        checkInline();
        inlineSteps.add(ai.agentican.framework.orchestration.model.WorkflowStepAgent.builder(stepName)
                .agent(agentName)
                .instructions(instructions)
                .build());
        return this;
    }

    /** Inline DSL: declare which step's output is the workflow's output. */
    public WorkflowBuilder outputStep(String stepName) {

        checkInline();
        this.inlineOutputStep = stepName;
        return this;
    }

    public <I> InputBound<I> input(Class<I> inputType) {

        if (inputType == null)
            throw new IllegalArgumentException("Input type is required (use Void.class for none)");

        return new InputBound<>(this, inputType);
    }

    /**
     * Build with default typing — {@code Workflow<Void, String>} that takes no
     * input and returns the workflow's output step value as a String. Use the
     * full {@code .input(Class).output(Class).build()} chain when typed I/O
     * matters.
     */
    public Workflow<Void, String> build() {

        return new Typed<Void, String>(this, String.class).build();
    }

    /**
     * Build a raw workflow — {@code Workflow<Map<String,String>, WorkflowRunResult>}
     * that takes the workflow's parameter map as input and returns the full
     * execution struct. For adapter modules (REST, reactive, scheduler) that
     * accept untyped plans deserialized from external sources. Application
     * code should use the typed {@code .input(...).output(...).build()} chain
     * instead.
     */
    @SuppressWarnings("unchecked")
    public Workflow<java.util.Map<String, String>, ai.agentican.framework.orchestration.execution.WorkflowRunResult> raw() {

        return new Typed<java.util.Map<String, String>,
                ai.agentican.framework.orchestration.execution.WorkflowRunResult>(this,
                ai.agentican.framework.orchestration.execution.WorkflowRunResult.class).build();
    }

    private void checkInline() {

        if (predefined != null)
            throw new IllegalStateException(
                    "Cannot use inline step DSL when the workflow was created with a predefined "
                    + "WorkflowDefinition. Either pass the definition only, or use the inline DSL "
                    + "starting from agentican.workflow(name).");
    }

    /** Resolve the workflow definition eagerly. */
    WorkflowDefinition resolvedDefinition() {

        if (predefined != null) return predefined;

        if (!inlineSteps.isEmpty()) {

            return WorkflowDefinition.builder(ai.agentican.framework.util.Ids.generate(), workflowName)
                    .description(inlineDescription)
                    .params(inlineParams)
                    .steps(inlineSteps)
                    .outputStep(inlineOutputStep)
                    .build();
        }

        var def = engine.registry().workflows().byName(workflowName);

        if (def == null)
            throw new IllegalStateException(
                    "No workflow definition named '" + workflowName + "' in the registry");

        return def;
    }

    WorkflowEngine engine() { return engine; }

    public static final class InputBound<I> {

        private final WorkflowBuilder parent;

        InputBound(WorkflowBuilder parent, Class<I> inputType) {

            // inputType is purely a type witness for I; not retained.
            this.parent = parent;
        }

        public <O> Typed<I, O> output(Class<O> outputType) {

            if (outputType == null)
                throw new IllegalArgumentException("Output type is required (use Void.class for none)");

            return new Typed<I, O>(parent, outputType);
        }

        public Workflow<I, Void> build() {

            return new Typed<I, Void>(parent, Void.class).build();
        }
    }

    public static final class Typed<I, O> {

        private final WorkflowBuilder parent;
        private final Class<O> outputType;

        Typed(WorkflowBuilder parent, Class<O> outputType) {

            this.parent = parent;
            this.outputType = outputType;
        }

        public Workflow<I, O> build() {

            return new Workflow<>(parent.engine, parent.resolvedDefinition(), outputType);
        }
    }
}
