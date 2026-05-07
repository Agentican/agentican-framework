package ai.agentican.framework;

import ai.agentican.framework.orchestration.model.WorkflowDefinition;
import ai.agentican.framework.orchestration.model.WorkflowStepAgent;

import java.util.List;

public final class TaskBuilder {

    static final String STEP_NAME = "invoke";

    private final WorkflowEngine engine;
    private final String taskName;

    private List<String> tools = List.of();
    private List<String> skills = List.of();

    private String agentName;
    private String instructions;

    private boolean hitl;
    private boolean persist;

    public TaskBuilder(WorkflowEngine engine, String taskName) {

        if (engine == null)
            throw new IllegalArgumentException("WorkflowEngine is required");

        if (taskName == null || taskName.isBlank())
            throw new IllegalArgumentException("Task name is required");

        this.engine = engine;
        this.taskName = taskName;
    }

    public TaskBuilder agent(String agentName) {

        if (agentName == null || agentName.isBlank())
            throw new IllegalArgumentException("Agent name is required");

        this.agentName = agentName;

        return this;
    }

    public TaskBuilder instructions(String instructions) {

        this.instructions = instructions;

        return this;
    }

    public TaskBuilder tools(String... tools) {

        this.tools = tools != null ? List.of(tools) : List.of();

        return this;
    }

    public TaskBuilder skills(String... skills) {

        this.skills = skills != null ? List.of(skills) : List.of();

        return this;
    }

    public TaskBuilder hitl(boolean hitl) {

        this.hitl = hitl;

        return this;
    }

    public TaskBuilder persist() {

        this.persist = true;

        return this;
    }

    public <I> InputBound<I> input(Class<I> inputType) {

        if (agentName == null)
            throw new IllegalStateException(".agent(...) must be called before .input(...)");

        if (instructions == null || instructions.isBlank())
            throw new IllegalStateException(".instructions(...) must be called before .input(...)");

        if (inputType == null)
            throw new IllegalArgumentException("Input type is required (use Void.class for none)");

        return new InputBound<>(this, inputType);
    }

    public static final class InputBound<I> {

        private final TaskBuilder parent;
        private final Class<I> inputType;

        InputBound(TaskBuilder parent, Class<I> inputType) {

            this.parent = parent;
            this.inputType = inputType;
        }

        public <O> Typed<I, O> output(Class<O> outputType) {

            if (outputType == null)
                throw new IllegalArgumentException("Output type is required (use Void.class for none)");

            return new Typed<I, O>(parent, inputType, outputType);
        }
    }

    public static final class Typed<I, O> {

        private final TaskBuilder parent;
        private final Class<I> inputType;
        private final Class<O> outputType;

        Typed(TaskBuilder parent, Class<I> inputType, Class<O> outputType) {

            this.parent = parent;
            this.inputType = inputType;
            this.outputType = outputType;
        }

        public Workflow<I, O> build() {

            if (parent.engine.registry().agents().byName(parent.agentName) == null)
                throw new IllegalStateException("No agent named '" + parent.agentName + "' is registered");

            var params = WorkflowInputMapper.paramsFor(inputType);

            var step = new WorkflowStepAgent(
                    STEP_NAME, parent.agentName, parent.instructions, null, parent.hitl, parent.skills, parent.tools);

            var workflow = WorkflowDefinition.builder(ai.agentican.framework.util.Ids.generate(), parent.taskName)
                    .params(params)
                    .steps(List.of(step))
                    .outputStep(STEP_NAME)
                    .build();

            if (parent.persist)
                parent.engine.registry().workflows().registerIfAbsent(workflow);

            return new Workflow<>(parent.engine, workflow, outputType);
        }
    }
}
