package ai.agentican.quarkus;

import jakarta.enterprise.util.Nonbinding;
import jakarta.inject.Qualifier;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Qualifier for injecting an {@link ai.agentican.framework.invoker.AgenticanTask}
 * bound to a multi-step plan by name. Mirrors the imperative
 * {@code agentican.workflowTask(name).plan(planName).build()} builder.
 *
 * <pre>
 * &#64;Inject &#64;WorkflowTask(name = "Check Fintech Account Health", plan = "Churn Risk Assessment")
 * AgenticanTask&lt;Account, ChurnAssessment&gt; assessment;
 * </pre>
 *
 * <p>The plan is resolved from {@code Agentican.registry().plans()} at run
 * time, so plans registered at runtime (via DB hydration, programmatic
 * registration, etc.) are picked up automatically.
 */
@Qualifier
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.TYPE})
public @interface WorkflowTask {

    @Nonbinding String name();

    @Nonbinding String plan();
}
