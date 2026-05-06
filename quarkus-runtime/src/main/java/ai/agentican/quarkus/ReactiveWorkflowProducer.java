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
    @AgenticanTask(name = "", agent = "", instructions = "")
    public <I, O> ReactiveWorkflow<I, O> produceTask(InjectionPoint ip) {

        var ann = TaskTypeArgs.qualifier(ip, AgenticanTask.class);
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
    @AgenticanWorkflow(name = "")
    public <I, O> ReactiveWorkflow<I, O> produceWorkflow(InjectionPoint ip) {

        var ann = TaskTypeArgs.qualifier(ip, AgenticanWorkflow.class);
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
