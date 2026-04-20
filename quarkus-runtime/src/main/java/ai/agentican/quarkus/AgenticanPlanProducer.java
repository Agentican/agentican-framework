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

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

/**
 * Produces typed {@link Agentican} beans for injection points qualified with
 * {@link AgenticanPlan}. The produced invoker resolves its plan by name on each
 * call, so plans registered at runtime (DB, programmatic) are honored.
 */
@ApplicationScoped
public class AgenticanPlanProducer {

    private static final Logger LOG = LoggerFactory.getLogger(AgenticanPlanProducer.class);

    @Inject
    AgenticanRuntime runtime;

    @Produces
    @Dependent
    @AgenticanPlan("")
    public <P, R> Agentican<P, R> produce(InjectionPoint ip) {

        var planName = ip.getQualifiers().stream()
                .filter(AgenticanPlan.class::isInstance)
                .map(AgenticanPlan.class::cast)
                .map(AgenticanPlan::value)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "@AgenticanPlan qualifier missing on injection point: " + ip));

        var typeArgs = resolveTypeArgs(ip);
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

    private static Class<?>[] resolveTypeArgs(InjectionPoint ip) {

        Type type = ip.getType();

        if (type instanceof ParameterizedType pt && pt.getActualTypeArguments().length == 2) {

            return new Class<?>[]{ asClass(pt.getActualTypeArguments()[0]),
                                   asClass(pt.getActualTypeArguments()[1]) };
        }

        throw new IllegalStateException(
                "@AgenticanPlan injection point must declare two type parameters, e.g. "
                        + "Agentican<MyParams, MyOutput>. Use Void for either if not needed. Got: " + type);
    }

    private static Class<?> asClass(Type arg) {

        if (arg instanceof Class<?> c) return c;
        if (arg instanceof ParameterizedType pa && pa.getRawType() instanceof Class<?> c) return c;
        throw new IllegalStateException("Unsupported type argument: " + arg);
    }
}
