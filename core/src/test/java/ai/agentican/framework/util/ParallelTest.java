package ai.agentican.framework.util;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;

class ParallelTest {

    @Test
    void emptyListReturnsEmpty() {

        var result = Parallel.map(List.of(), x -> x);

        assertTrue(result.isEmpty());
    }

    @Test
    void singleItemRunsSynchronously() {

        var result = Parallel.map(List.of("a"), String::toUpperCase);

        assertEquals(List.of("A"), result);
    }

    @Test
    void multipleItemsRunInParallel() {

        var threadNames = new ConcurrentHashMap<Integer, String>();

        var result = Parallel.map(List.of(1, 2, 3), item -> {

            threadNames.put(item, Thread.currentThread().getName());

            return item * 2;
        });

        assertEquals(List.of(2, 4, 6), result);
        assertEquals(3, threadNames.size());
    }

    @Test
    void propagatesMdc() {

        MDC.put("test-key", "test-value");

        try {

            var result = Parallel.map(List.of("a", "b"), item -> {

                assertEquals("test-value", MDC.get("test-key"));

                return item;
            });

            assertEquals(List.of("a", "b"), result);

            assertEquals("test-value", MDC.get("test-key"));
        }
        finally {

            MDC.clear();
        }
    }

    @Test
    void exceptionPropagates() {

        assertThrows(Exception.class, () ->
                Parallel.map(List.of("a", "b"), item -> {

                    if (item.equals("b")) throw new RuntimeException("boom");

                    return item;
                }));
    }
}
