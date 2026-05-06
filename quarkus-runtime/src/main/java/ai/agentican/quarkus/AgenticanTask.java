package ai.agentican.quarkus;

import jakarta.enterprise.util.Nonbinding;
import jakarta.inject.Qualifier;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * CDI qualifier for injecting a single-agent ad-hoc
 * {@link ai.agentican.framework.Workflow}. The annotation declares
 * the agent and instructions inline; the producer synthesizes a one-step
 * workflow definition around them.
 *
 * <pre>{@code
 * @Inject
 * @AgenticanTask(name = "Sentiment", agent = "sentiment-agent", instructions = "Classify the sentiment.")
 * Workflow<SentimentInput, String> sentimentTask;
 * }</pre>
 */
@Qualifier
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.TYPE})
public @interface AgenticanTask {

    @Nonbinding String name();

    @Nonbinding String agent();

    @Nonbinding String instructions();

    @Nonbinding String[] skills() default {};

    @Nonbinding String[] tools() default {};

    @Nonbinding boolean hitl() default false;
}
