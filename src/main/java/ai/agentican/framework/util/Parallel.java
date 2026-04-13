package ai.agentican.framework.util;

import org.slf4j.MDC;

import java.util.List;
import java.util.concurrent.StructuredTaskScope;
import java.util.concurrent.StructuredTaskScope.Subtask;
import java.util.function.Function;

public class Parallel {

    public static <T, R> List<R> map(List<T> items, Function<T, R> fn) {

        if (items.isEmpty())
            return List.of();

        if (items.size() == 1)
            return List.of(fn.apply(items.getFirst()));

        var mdc = MDC.getCopyOfContextMap();

        try (var scope = StructuredTaskScope.open(StructuredTaskScope.Joiner.allSuccessfulOrThrow())) {

            var subtasks = items.stream()
                    .map(item -> scope.fork(() -> {

                        if (mdc != null) MDC.setContextMap(mdc);

                        try {
                            return fn.apply(item);
                        }
                        finally {
                            MDC.clear();
                        }
                    }))
                    .toList();

            scope.join();

            return subtasks.stream()
                    .map(Subtask::get)
                    .toList();
        }
        catch (InterruptedException e) {

            Thread.currentThread().interrupt();
            throw new RuntimeException("Parallel execution interrupted", e);
        }
    }
}
