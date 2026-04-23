package ai.agentican.quarkus;

import ai.agentican.framework.Agentican;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Produces;
import jakarta.enterprise.inject.spi.InjectionPoint;
import jakarta.inject.Inject;

@ApplicationScoped
public class ReactiveAgenticanPlanProducer {

    @Inject
    Agentican runtime;

    @Produces
    @Dependent
    @AgenticanPlan("")
    public <P, R> ReactiveAgenticanTask<P, R> produce(InjectionPoint ip) {

        var planName = AgenticanPlanInjection.planName(ip);
        var typeArgs = AgenticanPlanInjection.typeArgs(ip);

        @SuppressWarnings("unchecked") Class<P> paramsType = (Class<P>) typeArgs[0];
        @SuppressWarnings("unchecked") Class<R> outputType = (Class<R>) typeArgs[1];

        return ReactiveAgenticanTask.of(
                runtime.workflowTask(planName).plan(planName).input(paramsType).output(outputType).build());
    }
}
