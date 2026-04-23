package ai.agentican.quarkus;

import jakarta.enterprise.util.Nonbinding;
import jakarta.inject.Qualifier;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Qualifier for injecting a typed {@link ai.agentican.framework.invoker.AgenticanTask}
 * bound to a plan by <b>name</b>.
 *
 * <pre>
 * &#64;Inject &#64;AgenticanPlan("triage")
 * AgenticanTask&lt;TriageParams&gt; triage;
 * </pre>
 *
 * <p>The plan is resolved from {@code Agentican.registry().plans()} on each
 * invocation, so plans registered at runtime (via DB hydration, programmatic
 * registration, etc.) are picked up automatically.
 */
@Qualifier
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.TYPE})
public @interface AgenticanPlan {

    @Nonbinding String value();
}
