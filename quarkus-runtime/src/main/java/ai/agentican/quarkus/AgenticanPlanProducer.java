package ai.agentican.quarkus;

import ai.agentican.framework.invoker.Agentican;
import ai.agentican.framework.AgenticanRuntime;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Produces;
import jakarta.enterprise.inject.spi.InjectionPoint;
import jakarta.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApplicationScoped
public class AgenticanPlanProducer {

    private static final Logger LOG = LoggerFactory.getLogger(AgenticanPlanProducer.class);

    @Inject
    AgenticanRuntime runtime;

    @Produces
    @Dependent
    @AgenticanPlan("")
    public <P, R> Agentican<P, R> produce(InjectionPoint ip) {

        var planName = AgenticanPlanInjection.planName(ip);
        var typeArgs = AgenticanPlanInjection.typeArgs(ip);

        @SuppressWarnings("unchecked") Class<P> paramsType = (Class<P>) typeArgs[0];
        @SuppressWarnings("unchecked") Class<R> outputType = (Class<R>) typeArgs[1];

        var plan = runtime.registry().plans().get(planName);
        if (plan == null) {
            LOG.warn("@AgenticanPlan(\"{}\"): plan not in registry at injection time; will resolve at each run",
                    planName);
        } else {
            LOG.info("@AgenticanPlan(\"{}\"): resolved (params = {}, output = {})",
                    planName, plan.params().stream().map(p -> p.name()).toList(), outputType.getSimpleName());
        }

        return runtime.agentican(planName, paramsType, outputType);
    }
}
