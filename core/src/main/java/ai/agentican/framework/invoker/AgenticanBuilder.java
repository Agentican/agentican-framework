package ai.agentican.framework.invoker;

import ai.agentican.framework.Agentican;
import ai.agentican.framework.orchestration.model.Plan;

public final class AgenticanBuilder {

    private final Agentican runtime;
    private final String taskName;
    private String planName;
    private Plan plan;

    public AgenticanBuilder(Agentican runtime, String taskName) {

        if (runtime == null)
            throw new IllegalArgumentException("Agentican is required");
        if (taskName == null || taskName.isBlank())
            throw new IllegalArgumentException("Task name is required");

        this.runtime = runtime;
        this.taskName = taskName;
    }

    public AgenticanBuilder plan(String planName) {

        if (planName == null || planName.isBlank())
            throw new IllegalArgumentException("Plan name is required");

        this.planName = planName;
        this.plan = null;
        return this;
    }

    public AgenticanBuilder plan(Plan plan) {

        if (plan == null)
            throw new IllegalArgumentException("Plan is required");

        this.plan = plan;
        this.planName = null;
        return this;
    }

    public <I> InputBound<I> input(Class<I> inputType) {

        if (inputType == null)
            throw new IllegalArgumentException("Input type is required (use Void.class for none)");

        return new InputBound<>(this, inputType);
    }

    public static final class InputBound<I> {

        private final AgenticanBuilder parent;
        private final Class<I> inputType;

        InputBound(AgenticanBuilder parent, Class<I> inputType) {

            this.parent = parent;
            this.inputType = inputType;
        }

        public <O> Typed<I, O> output(Class<O> outputType) {

            if (outputType == null)
                throw new IllegalArgumentException("Output type is required (use Void.class for none)");

            return new Typed<>(parent, inputType, outputType);
        }

        public AgenticanTask<I, Void> build() {

            return new Typed<>(parent, inputType, Void.class).build();
        }
    }

    public static final class Typed<I, O> {

        private final AgenticanBuilder parent;
        private final Class<I> inputType;
        private final Class<O> outputType;

        Typed(AgenticanBuilder parent, Class<I> inputType, Class<O> outputType) {

            this.parent = parent;
            this.inputType = inputType;
            this.outputType = outputType;
        }

        public AgenticanTask<I, O> build() {

            if (parent.plan == null && (parent.planName == null || parent.planName.isBlank()))
                throw new IllegalStateException("plan(...) is required");

            if (parent.plan != null)
                return AgenticanTask.forPlan(parent.runtime, parent.plan, inputType, outputType);

            return AgenticanTask.byName(parent.runtime, parent.planName, inputType, outputType);
        }
    }
}
