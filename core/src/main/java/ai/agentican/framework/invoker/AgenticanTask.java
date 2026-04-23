package ai.agentican.framework.invoker;

import ai.agentican.framework.Agentican;
import ai.agentican.framework.orchestration.execution.TaskHandle;
import ai.agentican.framework.orchestration.execution.TaskResult;
import ai.agentican.framework.orchestration.model.Plan;

import java.util.concurrent.CompletableFuture;

public interface AgenticanTask<P, R> {

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

    static <P, R> AgenticanTask<P, R> forPlan(Agentican runtime, Plan plan,
                                              Class<P> paramsType, Class<R> outputType) {

        AgenticanOutputReader.resolveOutputStep(plan, outputType);

        return new DefaultAgenticanTask<>(runtime, () -> plan, paramsType, outputType);
    }

    static <P, R> AgenticanTask<P, R> byName(Agentican runtime, String planName,
                                             Class<P> paramsType, Class<R> outputType) {

        return new DefaultAgenticanTask<>(runtime, () -> {

            var plan = runtime.registry().plans().get(planName);

            if (plan == null)
                throw new IllegalStateException("No plan named '" + planName + "' in the registry");

            return plan;

        }, paramsType, outputType);
    }
}
