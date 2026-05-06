package ai.agentican.quarkus.otel;

import ai.agentican.framework.orchestration.execution.WorkflowRunListener;
import ai.agentican.framework.orchestration.execution.WorkflowRunDecorator;
import ai.agentican.framework.llm.LlmClientDecorator;
import ai.agentican.framework.store.WorkflowRunStore;
import io.opentelemetry.api.trace.Tracer;
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@ApplicationScoped
public class TracingAutoConfiguration {

    @Inject
    Tracer tracer;

    @Inject
    WorkflowRunStore workflowRunStore;

    @Produces
    @ApplicationScoped
    public WorkflowRunListener tracedLifecycleListener() {

        return new TracedLifecycleListener(tracer, workflowRunStore);
    }

    @Produces
    @ApplicationScoped
    public WorkflowRunDecorator tracedTaskDecorator() {

        return new TracedTaskDecorator();
    }

    @Produces
    @ApplicationScoped
    public LlmClientDecorator tracedLlmDecorator() {

        return (config, client) ->
                new TracedLlmClient(config.name(), config.model(), client, tracer);
    }

    @Produces
    @Singleton
    @DefaultBean
    public InMemorySpanExporter inMemorySpanExporter() {

        return new InMemorySpanExporter();
    }
}
