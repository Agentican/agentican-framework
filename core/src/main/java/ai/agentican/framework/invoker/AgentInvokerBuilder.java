package ai.agentican.framework.invoker;

import ai.agentican.framework.Agentican;
import ai.agentican.framework.orchestration.model.Plan;
import ai.agentican.framework.orchestration.model.PlanStepAgent;

import java.util.List;

public final class AgentInvokerBuilder {

    static final String STEP_NAME = "invoke";

    private final Agentican runtime;
    private final String taskName;
    private String agentName;

    public AgentInvokerBuilder(Agentican runtime, String taskName) {

        if (runtime == null)
            throw new IllegalArgumentException("Agentican is required");
        if (taskName == null || taskName.isBlank())
            throw new IllegalArgumentException("Task name is required");

        this.runtime = runtime;
        this.taskName = taskName;
    }

    public AgentInvokerBuilder agent(String agentName) {

        if (agentName == null || agentName.isBlank())
            throw new IllegalArgumentException("Agent name is required");

        this.agentName = agentName;
        return this;
    }

    public <I> InputBound<I> input(Class<I> inputType) {

        if (inputType == null)
            throw new IllegalArgumentException("Input type is required (use Void.class for none)");

        return new InputBound<>(this, inputType);
    }

    public static final class InputBound<I> {

        private final AgentInvokerBuilder parent;
        private final Class<I> inputType;

        InputBound(AgentInvokerBuilder parent, Class<I> inputType) {

            this.parent = parent;
            this.inputType = inputType;
        }

        public <O> Typed<I, O> output(Class<O> outputType) {

            if (outputType == null)
                throw new IllegalArgumentException("Output type is required (use Void.class for none)");

            return new Typed<>(parent, inputType, outputType);
        }
    }

    public static final class Typed<I, O> {

        private final AgentInvokerBuilder parent;
        private final Class<I> inputType;
        private final Class<O> outputType;

        private String instructions;
        private List<String> tools = List.of();
        private List<String> skills = List.of();
        private boolean hitl;
        private boolean persist;

        Typed(AgentInvokerBuilder parent, Class<I> inputType, Class<O> outputType) {

            this.parent = parent;
            this.inputType = inputType;
            this.outputType = outputType;
        }

        public Typed<I, O> instructions(String instructions) {

            this.instructions = instructions;
            return this;
        }

        public Typed<I, O> tools(String... tools) {

            this.tools = tools != null ? List.of(tools) : List.of();
            return this;
        }

        public Typed<I, O> skills(String... skills) {

            this.skills = skills != null ? List.of(skills) : List.of();
            return this;
        }

        public Typed<I, O> hitl(boolean hitl) {

            this.hitl = hitl;
            return this;
        }

        public Typed<I, O> persist() {

            this.persist = true;
            return this;
        }

        public AgenticanTask<I, O> build() {

            if (parent.agentName == null || parent.agentName.isBlank())
                throw new IllegalStateException("agent(...) is required");

            if (instructions == null || instructions.isBlank())
                throw new IllegalStateException("instructions(...) is required");

            var agent = parent.runtime.registry().agents().getByName(parent.agentName);

            if (agent == null)
                throw new IllegalStateException("No agent named '" + parent.agentName + "' is registered");

            var params = AgenticanParamMapper.paramsFor(inputType);

            var step = new PlanStepAgent(
                    STEP_NAME, agent.id(), instructions, null, hitl, skills, tools);

            var plan = Plan.builder(parent.taskName)
                    .params(params)
                    .step(step)
                    .outputStep(STEP_NAME)
                    .build();

            if (persist)
                parent.runtime.registry().plans().registerIfAbsent(plan);

            return AgenticanTask.forPlan(parent.runtime, plan, inputType, outputType);
        }
    }
}
