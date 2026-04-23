package ai.agentican.quarkus;

import ai.agentican.framework.Agentican;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Produces;
import jakarta.enterprise.inject.spi.InjectionPoint;
import jakarta.inject.Inject;

/**
 * Reactive counterpart to {@link AgenticanTaskProducer}. Produces
 * {@link ReactiveAgenticanTask} beans for injection points qualified with
 * {@link AgentTask} or {@link WorkflowTask}.
 */
@ApplicationScoped
public class ReactiveAgenticanTaskProducer {

    @Inject
    Agentican agentican;

    @Produces
    @Dependent
    @AgentTask(name = "", agent = "", instructions = "")
    public <I, O> ReactiveAgenticanTask<I, O> produceAgentTask(InjectionPoint ip) {

        var ann = TaskTypeArgs.qualifier(ip, AgentTask.class);
        var types = TaskTypeArgs.of(ip);

        @SuppressWarnings("unchecked") Class<I> inputType = (Class<I>) types[0];
        @SuppressWarnings("unchecked") Class<O> outputType = (Class<O>) types[1];

        var task = agentican.agentTask(ann.name())
                .agent(ann.agent())
                .input(inputType)
                .output(outputType)
                .skills(ann.skills())
                .tools(ann.tools())
                .instructions(ann.instructions())
                .hitl(ann.hitl())
                .build();

        return ReactiveAgenticanTask.of(task);
    }

    @Produces
    @Dependent
    @WorkflowTask(name = "", plan = "")
    public <I, O> ReactiveAgenticanTask<I, O> produceWorkflowTask(InjectionPoint ip) {

        var ann = TaskTypeArgs.qualifier(ip, WorkflowTask.class);
        var types = TaskTypeArgs.of(ip);

        @SuppressWarnings("unchecked") Class<I> inputType = (Class<I>) types[0];
        @SuppressWarnings("unchecked") Class<O> outputType = (Class<O>) types[1];

        var task = agentican.workflowTask(ann.name())
                .plan(ann.plan())
                .input(inputType)
                .output(outputType)
                .build();

        return ReactiveAgenticanTask.of(task);
    }
}
