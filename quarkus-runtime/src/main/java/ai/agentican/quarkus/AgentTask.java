package ai.agentican.quarkus;

import jakarta.enterprise.util.Nonbinding;
import jakarta.inject.Qualifier;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

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
