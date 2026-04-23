package ai.agentican.quarkus;

import ai.agentican.framework.Agentican;
import ai.agentican.framework.invoker.AgenticanTask;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Produces;
import jakarta.enterprise.inject.spi.InjectionPoint;
import jakarta.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Produces {@link AgenticanTask} beans for injection points qualified with
 * {@link AgentTask} or {@link WorkflowTask}. Each method reads the annotation
 * from the injection point and delegates to the imperative builder.
 */
@ApplicationScoped
public class AgenticanTaskProducer {

    private static final Logger LOG = LoggerFactory.getLogger(AgenticanTaskProducer.class);

    @Inject
    Agentican agentican;

    @Produces
    @Dependent
    @AgentTask(name = "", agent = "", instructions = "")
    public <I, O> AgenticanTask<I, O> produceAgentTask(InjectionPoint ip) {

        var ann = TaskTypeArgs.qualifier(ip, AgentTask.class);
        var types = TaskTypeArgs.of(ip);

        @SuppressWarnings("unchecked") Class<I> inputType = (Class<I>) types[0];
        @SuppressWarnings("unchecked") Class<O> outputType = (Class<O>) types[1];

        LOG.info("@AgentTask(name=\"{}\", agent=\"{}\"): binding I={} O={}",
                ann.name(), ann.agent(), inputType.getSimpleName(), outputType.getSimpleName());

        return agentican.agentTask(ann.name())
                .agent(ann.agent())
                .input(inputType)
                .output(outputType)
                .skills(ann.skills())
                .tools(ann.tools())
                .instructions(ann.instructions())
                .hitl(ann.hitl())
                .build();
    }

    @Produces
    @Dependent
    @WorkflowTask(name = "", plan = "")
    public <I, O> AgenticanTask<I, O> produceWorkflowTask(InjectionPoint ip) {

        var ann = TaskTypeArgs.qualifier(ip, WorkflowTask.class);
        var types = TaskTypeArgs.of(ip);

        @SuppressWarnings("unchecked") Class<I> inputType = (Class<I>) types[0];
        @SuppressWarnings("unchecked") Class<O> outputType = (Class<O>) types[1];

        var plan = agentican.registry().plans().get(ann.plan());

        if (plan == null) {
            LOG.warn("@WorkflowTask(name=\"{}\", plan=\"{}\"): plan not in registry at injection time; "
                    + "will resolve at each run", ann.name(), ann.plan());
        } else {
            LOG.info("@WorkflowTask(name=\"{}\", plan=\"{}\"): resolved (params = {}, output = {})",
                    ann.name(), ann.plan(),
                    plan.params().stream().map(p -> p.name()).toList(),
                    outputType.getSimpleName());
        }

        return agentican.workflowTask(ann.name())
                .plan(ann.plan())
                .input(inputType)
                .output(outputType)
                .build();
    }
}
