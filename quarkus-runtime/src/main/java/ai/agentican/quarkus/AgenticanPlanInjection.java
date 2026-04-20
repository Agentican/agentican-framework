package ai.agentican.quarkus;

import jakarta.enterprise.inject.spi.InjectionPoint;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

final class AgenticanPlanInjection {

    private AgenticanPlanInjection() {}

    static String planName(InjectionPoint ip) {

        return ip.getQualifiers().stream()
                .filter(AgenticanPlan.class::isInstance)
                .map(AgenticanPlan.class::cast)
                .map(AgenticanPlan::value)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "@AgenticanPlan qualifier missing on injection point: " + ip));
    }

    static Class<?>[] typeArgs(InjectionPoint ip) {

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
