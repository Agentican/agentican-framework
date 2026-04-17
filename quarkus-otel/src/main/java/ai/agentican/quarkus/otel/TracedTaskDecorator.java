package ai.agentican.quarkus.otel;

import ai.agentican.framework.TaskDecorator;
import io.opentelemetry.context.Context;

import java.util.function.Supplier;

public class TracedTaskDecorator implements TaskDecorator {

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
    public TaskDecorator snapshot() {

        var snapshotContext = Context.current();

        return new TaskDecorator() {

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
