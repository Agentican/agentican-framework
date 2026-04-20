package ai.agentican.framework.orchestration.execution;

import java.util.function.Supplier;

@FunctionalInterface
public interface TaskDecorator {

    <T> Supplier<T> decorate(Supplier<T> task);

    default Runnable decorate(Runnable task) {

        var decorated = decorate(() -> { task.run(); return null; });

        return decorated::get;
    }

    default TaskDecorator snapshot() {

        return this;
    }
}
