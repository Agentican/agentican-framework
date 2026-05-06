package ai.agentican.quarkus.metrics;

import ai.agentican.framework.orchestration.execution.WorkflowRunListener;
import ai.agentican.framework.llm.LlmClientDecorator;

import ai.agentican.framework.store.WorkflowRunStore;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;

@ApplicationScoped
public class MetricsAutoConfiguration {

    @Inject
    MeterRegistry registry;

    @Inject
    WorkflowRunStore workflowRunStore;

    @Produces
    @ApplicationScoped
    public LlmClientDecorator meteredLlmDecorator() {

        return (config, client) ->
                new MeteredLlmClient(config.name(), config.model(), client, registry);
    }

    @Produces
    @ApplicationScoped
    public WorkflowRunListener meteredTurnListener() {

        return new MeteredTurnListener(registry, workflowRunStore);
    }
}
