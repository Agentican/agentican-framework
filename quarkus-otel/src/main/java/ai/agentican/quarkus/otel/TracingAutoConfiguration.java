package ai.agentican.quarkus.otel;

import ai.agentican.framework.orchestration.execution.TaskListener;
import ai.agentican.framework.orchestration.execution.TaskDecorator;
import ai.agentican.framework.llm.LlmClientDecorator;
import ai.agentican.framework.store.TaskStateStore;
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
    TaskStateStore taskStateStore;

    @Produces
    @ApplicationScoped
    public TaskListener tracedLifecycleListener() {

        return new TracedLifecycleListener(tracer, taskStateStore);
    }

    @Produces
    @ApplicationScoped
    public TaskDecorator tracedTaskDecorator() {

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
