package ai.agentican.quarkus;

import jakarta.enterprise.inject.spi.InjectionPoint;

import java.lang.annotation.Annotation;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

/**
 * Shared utility for {@link AgentTask} / {@link WorkflowTask} producers:
 * extracts the {@code <I, O>} type arguments from an injection point and
 * locates the qualifier annotation on it.
 */
final class TaskTypeArgs {

    private TaskTypeArgs() {}

    static Class<?>[] of(InjectionPoint ip) {

        Type type = ip.getType();

        if (type instanceof ParameterizedType pt && pt.getActualTypeArguments().length == 2) {

            return new Class<?>[] {
                    asClass(pt.getActualTypeArguments()[0]),
                    asClass(pt.getActualTypeArguments()[1])
            };
        }

        throw new IllegalStateException(
                "Task injection point must declare two type parameters, e.g. "
                        + "AgenticanTask<MyInput, MyOutput>. Use Void for either if not needed. Got: " + type);
    }

    static <A extends Annotation> A qualifier(InjectionPoint ip, Class<A> annotationType) {

        return ip.getQualifiers().stream()
                .filter(annotationType::isInstance)
                .map(annotationType::cast)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "@" + annotationType.getSimpleName()
                                + " qualifier missing on injection point: " + ip));
    }

    private static Class<?> asClass(Type arg) {

        if (arg instanceof Class<?> c) return c;
        if (arg instanceof ParameterizedType pa && pa.getRawType() instanceof Class<?> c) return c;
        throw new IllegalStateException("Unsupported type argument: " + arg);
    }
}
