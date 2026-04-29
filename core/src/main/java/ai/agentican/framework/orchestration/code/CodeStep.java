package ai.agentican.framework.orchestration.code;

@FunctionalInterface
public interface CodeStep<I, O> {

    O execute(I input, StepContext context);
}
