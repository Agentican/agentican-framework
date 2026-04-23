package ai.agentican.quarkus;

import jakarta.enterprise.util.Nonbinding;
import jakarta.inject.Qualifier;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Qualifier for injecting an {@link ai.agentican.framework.invoker.AgenticanTask}
 * backed by a single-step agent invocation. Mirrors the imperative
 * {@code agentican.agentTask(...).agent(...).skills(...).instructions(...).build()}
 * builder.
 *
 * <pre>
 * &#64;Inject &#64;AgentTask(
 *     name         = "Audit Payments Alert",
 *     agent        = "Site Reliability Engineer",
 *     skills       = { "Alert quality checklist" },
 *     instructions = """
 *                    Review this alert …
 *                    """)
 * AgenticanTask&lt;AlertDefinition, AlertCritique&gt; reviewer;
 * </pre>
 *
 * <p>All members are {@link Nonbinding}: the qualifier's identity is the
 * annotation type only, and values are read from the {@link
 * jakarta.enterprise.inject.spi.InjectionPoint} by the producer. Values must
 * be compile-time constants, so referenced strings need to be declared as
 * {@code public static final String}.
 */
@Qualifier
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.TYPE})
public @interface AgentTask {

    @Nonbinding String name();

    @Nonbinding String agent();

    @Nonbinding String instructions();

    @Nonbinding String[] skills() default {};

    @Nonbinding String[] tools() default {};

    @Nonbinding boolean hitl() default false;
}
