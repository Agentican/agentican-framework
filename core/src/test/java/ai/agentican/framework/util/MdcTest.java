package ai.agentican.framework.util;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class MdcTest {

    @Test
    void propagateSupplierPreservesMdc() throws Exception {

        MDC.put("key", "value");

        try {

            var supplier = Mdc.propagate(() -> MDC.get("key"));

            var captured = new AtomicReference<String>();

            var thread = Thread.ofVirtual().start(() -> captured.set(supplier.get()));

            thread.join();

            assertEquals("value", captured.get());
        }
        finally {
            MDC.clear();
        }
    }

    @Test
    void propagateRunnablePreservesMdc() throws Exception {

        MDC.put("key", "value");

        try {

            var captured = new AtomicReference<String>();

            var runnable = Mdc.propagate((Runnable) () -> captured.set(MDC.get("key")));

            var thread = Thread.ofVirtual().start(runnable);

            thread.join();

            assertEquals("value", captured.get());
        }
        finally {
            MDC.clear();
        }
    }

    @Test
    void propagateCleansMdcAfterExecution() throws Exception {

        MDC.put("key", "value");

        try {

            var supplier = Mdc.propagate(() -> MDC.get("key"));

            var mdcAfter = new AtomicReference<String>();

            var thread = Thread.ofVirtual().start(() -> {

                supplier.get();
                mdcAfter.set(MDC.get("key"));
            });

            thread.join();

            assertNull(mdcAfter.get());
        }
        finally {
            MDC.clear();
        }
    }

    @Test
    void propagateWithNullContext() {

        MDC.clear();

        var supplier = Mdc.propagate(() -> "ok");

        assertEquals("ok", supplier.get());
    }
}
