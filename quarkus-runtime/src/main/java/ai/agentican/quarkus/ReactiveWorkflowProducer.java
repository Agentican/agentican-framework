package ai.agentican.quarkus;

import ai.agentican.framework.Agentican;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Produces;
import jakarta.enterprise.inject.spi.InjectionPoint;
import jakarta.inject.Inject;

@ApplicationScoped
public class ReactiveWorkflowProducer {

    @Inject
    Agentican agentican;

    @Produces
    @Dependent
    @Task(name = "", agent = "", instructions = "")
    public <I, O> ReactiveWorkflow<I, O> produceTask(InjectionPoint ip) {

        var ann = TaskTypeArgs.qualifier(ip, Task.class);
        var types = TaskTypeArgs.of(ip);

        @SuppressWarnings("unchecked") Class<I> inputType = (Class<I>) types[0];
        @SuppressWarnings("unchecked") Class<O> outputType = (Class<O>) types[1];

        var task = agentican.task(ann.name())
                .agent(ann.agent())
                .instructions(ann.instructions())
                .skills(ann.skills())
                .tools(ann.tools())
                .hitl(ann.hitl())
                .input(inputType)
                .output(outputType)
                .build();

        return ReactiveWorkflow.of(task);
    }

    @Produces
    @Dependent
    @ai.agentican.quarkus.Workflow(name = "")
    public <I, O> ReactiveWorkflow<I, O> produceWorkflow(InjectionPoint ip) {

        var ann = TaskTypeArgs.qualifier(ip, ai.agentican.quarkus.Workflow.class);
        var types = TaskTypeArgs.of(ip);

        @SuppressWarnings("unchecked") Class<I> inputType = (Class<I>) types[0];
        @SuppressWarnings("unchecked") Class<O> outputType = (Class<O>) types[1];

        var task = agentican.workflow(ann.name())
                .input(inputType)
                .output(outputType)
                .build();

        return ReactiveWorkflow.of(task);
    }
}
