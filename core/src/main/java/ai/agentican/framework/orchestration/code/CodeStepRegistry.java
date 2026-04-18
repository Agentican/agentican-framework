package ai.agentican.framework.orchestration.code;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Holds the {@link CodeStep} implementations registered with an {@code Agentican}
 * instance, keyed by slug. Each entry pairs a {@link CodeStepSpec} (which
 * carries the typed input/output classes) with the executor itself.
 */
public class CodeStepRegistry {

    /**
     * A registered code step: its {@link CodeStepSpec} and the executor that
     * implements it. Wildcards on the type parameters allow uniform storage —
     * the runner re-casts at dispatch time.
     */
    public record Registered(CodeStepSpec<?, ?> spec, CodeStep<?, ?> executor) { }

    private final Map<String, Registered> entries = new LinkedHashMap<>();

    public <I, O> void register(CodeStepSpec<I, O> spec, CodeStep<I, O> executor) {

        if (spec == null)
            throw new IllegalArgumentException("Code step spec is required");

        if (executor == null)
            throw new IllegalArgumentException("Code step executor is required for slug '" + spec.slug() + "'");

        if (entries.containsKey(spec.slug()))
            throw new IllegalStateException("Code step slug '" + spec.slug() + "' is already registered");

        entries.put(spec.slug(), new Registered(spec, executor));
    }

    public Registered get(String slug) {

        return entries.get(slug);
    }

    public boolean contains(String slug) {

        return entries.containsKey(slug);
    }

    public Set<String> slugs() {

        return Set.copyOf(entries.keySet());
    }

    public int size() {

        return entries.size();
    }
}
