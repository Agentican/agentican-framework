package ai.agentican.quarkus.otel;

import ai.agentican.framework.orchestration.execution.WorkflowRunDecorator;
import io.opentelemetry.context.Context;

import java.util.function.Supplier;

public class TracedTaskDecorator implements WorkflowRunDecorator {

    @Override
    public <T> Supplier<T> decorate(Supplier<T> task) {

        var capturedContext = Context.current();

        return () -> {

            try (var scope = capturedContext.makeCurrent()) {

                return task.get();
            }
        };
    }

    @Override
    public WorkflowRunDecorator snapshot() {

        var snapshotContext = Context.current();

        return new WorkflowRunDecorator() {

            @Override
            public <T> Supplier<T> decorate(Supplier<T> task) {

                return () -> {

                    try (var scope = snapshotContext.makeCurrent()) {

                        return task.get();
                    }
                };
            }
        };
    }
}
