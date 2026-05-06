package ai.agentican.quarkus;

import jakarta.enterprise.util.Nonbinding;
import jakarta.inject.Qualifier;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * CDI qualifier for injecting a typed
 * {@link ai.agentican.framework.Workflow} bound to a registered
 * {@link ai.agentican.framework.orchestration.model.WorkflowDefinition}.
 *
 * <pre>{@code
 * @Inject
 * @ai.agentican.quarkus.Workflow(name = "Email Labeling")
 * Workflow<LabelingInput, LabelingResults> labelingWorkflow;
 * }</pre>
 *
 * <p>The {@code name} field doubles as both the CDI qualifier name (for
 * disambiguating injection sites) and the workflow definition's lookup key
 * in the registry. If multiple typed beans need to bind to the same
 * definition with different I/O typings, declare manual {@code @Produces}
 * methods instead.
 *
 * <p>This annotation shares its simple name with
 * {@link ai.agentican.framework.Workflow}; in source files that
 * import both, fully-qualify one (typically the annotation at the
 * injection site).
 */
@Qualifier
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.TYPE})
public @interface Workflow {

    @Nonbinding String name();
}
