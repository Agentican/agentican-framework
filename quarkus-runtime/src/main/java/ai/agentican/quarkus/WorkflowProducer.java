package ai.agentican.quarkus;

import ai.agentican.framework.Agentican;
import ai.agentican.framework.Workflow;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Produces;
import jakarta.enterprise.inject.spi.InjectionPoint;
import jakarta.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApplicationScoped
public class WorkflowProducer {

    private static final Logger LOG = LoggerFactory.getLogger(WorkflowProducer.class);

    @Inject
    Agentican agentican;

    @Produces
    @Dependent
    @AgenticanTask(name = "", agent = "", instructions = "")
    public <I, O> Workflow<I, O> produceTask(InjectionPoint ip) {

        var ann = TaskTypeArgs.qualifier(ip, AgenticanTask.class);
        var types = TaskTypeArgs.of(ip);

        @SuppressWarnings("unchecked") Class<I> inputType = (Class<I>) types[0];
        @SuppressWarnings("unchecked") Class<O> outputType = (Class<O>) types[1];

        LOG.info("@AgenticanTask(name=\"{}\", agent=\"{}\"): binding I={} O={}",
                ann.name(), ann.agent(), inputType.getSimpleName(), outputType.getSimpleName());

        return agentican.task(ann.name())
                .agent(ann.agent())
                .instructions(ann.instructions())
                .skills(ann.skills())
                .tools(ann.tools())
                .hitl(ann.hitl())
                .input(inputType)
                .output(outputType)
                .build();
    }

    @Produces
    @Dependent
    @AgenticanWorkflow(name = "")
    public <I, O> Workflow<I, O> produceWorkflow(InjectionPoint ip) {

        var ann = TaskTypeArgs.qualifier(ip, AgenticanWorkflow.class);
        var types = TaskTypeArgs.of(ip);

        @SuppressWarnings("unchecked") Class<I> inputType = (Class<I>) types[0];
        @SuppressWarnings("unchecked") Class<O> outputType = (Class<O>) types[1];

        var workflow = agentican.workflow(ann.name())
                .input(inputType)
                .output(outputType)
                .build();

        LOG.info("@AgenticanWorkflow(name=\"{}\"): resolved (output = {})",
                ann.name(), outputType.getSimpleName());

        return workflow;
    }
}
