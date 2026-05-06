package ai.agentican.framework.registry;

import java.util.Collection;
import java.util.Map;

public interface Catalog<T> {

    T register(T item);

    T registerIfAbsent(T item);

    T byId(String id);

    T byName(String name);

    boolean hasById(String id);

    boolean hasByName(String name);

    Collection<T> list();

    Map<String, T> asMap();

    default void delete(String ref) {

        throw new UnsupportedOperationException(getClass().getSimpleName() + " is read-only; delete not supported");
    }
}
