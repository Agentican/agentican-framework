package ai.agentican.framework.util;

import org.slf4j.MDC;

import java.util.function.Supplier;

public class Mdc {

    public static <T> Supplier<T> propagate(Supplier<T> supplier) {

        var ctx = MDC.getCopyOfContextMap();

        return () -> {

            if (ctx != null) MDC.setContextMap(ctx);

            try {

                return supplier.get();
            }
            finally {

                MDC.clear();
            }
        };
    }

    public static Runnable propagate(Runnable runnable) {

        var ctx = MDC.getCopyOfContextMap();

        return () -> {

            if (ctx != null) MDC.setContextMap(ctx);

            try {

                runnable.run();
            }
            finally {

                MDC.clear();
            }
        };
    }
}
