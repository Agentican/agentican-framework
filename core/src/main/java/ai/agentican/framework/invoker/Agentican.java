package ai.agentican.framework.invoker;

import ai.agentican.framework.AgenticanRuntime;
import ai.agentican.framework.orchestration.execution.TaskHandle;
import ai.agentican.framework.orchestration.execution.TaskResult;
import ai.agentican.framework.orchestration.model.Plan;

import java.util.concurrent.CompletableFuture;

public interface Agentican<P, R> {

    TaskHandle run(P params);

    default TaskHandle run() {

        return run(null);
    }

    R runAndAwait(P params);

    default R runAndAwait() {

        return runAndAwait(null);
    }

    CompletableFuture<R> runAsync(P params);

    default CompletableFuture<R> runAsync() {

        return runAsync(null);
    }

    default TaskResult awaitTaskResult(P params) {

        return run(params).result();
    }

    default TaskResult awaitTaskResult() {

        return awaitTaskResult(null);
    }

    /** Captures a {@link Plan} reference. Eagerly validates the plan has a resolvable output step. */
    static <P, R> Agentican<P, R> forPlan(AgenticanRuntime runtime, Plan plan,
                                          Class<P> paramsType, Class<R> outputType) {

        AgenticanOutputReader.resolveOutputStep(plan, outputType);

        return new DefaultAgentican<>(runtime, () -> plan, paramsType, outputType);
    }

    /** Resolves the plan from the registry by name on each invocation. */
    static <P, R> Agentican<P, R> byName(AgenticanRuntime runtime, String planName,
                                         Class<P> paramsType, Class<R> outputType) {

        return new DefaultAgentican<>(runtime, () -> {

            var plan = runtime.registry().plans().get(planName);
            if (plan == null)
                throw new IllegalStateException("No plan named '" + planName + "' in the registry");
            return plan;

        }, paramsType, outputType);
    }
}
